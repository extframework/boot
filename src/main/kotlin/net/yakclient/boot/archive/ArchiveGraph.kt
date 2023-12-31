package net.yakclient.boot.archive

import asOutput
import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.yakclient.common.util.*
import net.yakclient.common.util.resource.SafeResource
import net.yakclient.`object`.MutableObjectContainer
import net.yakclient.`object`.ObjectContainerImpl
import orThrow
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.reflect.KClass

public open class ArchiveGraph(
    public val path: Path,
    private val mutable: MutableMap<ArtifactMetadata.Descriptor, ArchiveNode<*>> = HashMap()
) : Map<ArtifactMetadata.Descriptor, ArchiveNode<*>> by mutable {
    private val resolvers: MutableObjectContainer<ArchiveNodeResolver<*, *, *, *, *, *>> = ObjectContainerImpl()

    // Public API
    public fun registerResolver(resolver: ArchiveNodeResolver<*, *, *, *, *, *>) {
        resolvers.register(resolver.name, resolver)
    }

    public suspend fun <K : ArtifactMetadata.Descriptor, T : ArchiveNode<T>> get(
        descriptor: K,
        resolver: ArchiveNodeResolver<K, *, T, *, *, *>
    ): JobResult<T, ArchiveException> = withContext(ArchiveTraceFactory) {
        getInternal(descriptor, ArchiveTrace(descriptor, null), resolver)
    }

    public suspend fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, R : RepositorySettings, RStub : RepositoryStub> cache(
        request: T,
        repository: R,
        resolver: ArchiveNodeResolver<D, T, *, R, RStub, *>
    ): JobResult<Unit, ArchiveException> =
        withContext(ArchiveTraceFactory) {
            cacheInternal<D, T, R, RStub>(request, repository, resolver)
        }


    // Internal

    private fun checkRegistration(resolver: ArchiveNodeResolver<*, *, *, *, *, *>) {
        if (resolvers.has(resolver.name)) return
        registerResolver(resolver)
    }
    private suspend fun <K : ArtifactMetadata.Descriptor, T : ArchiveNode<T>> getInternal(
        descriptor: K,
        trace: ArchiveTrace,
        resolver: ArchiveNodeResolver<K, *, T, *, *, *>
    ): JobResult<T, ArchiveException> {
        checkRegistration(resolver)
        return mutable[descriptor]?.let {
            check(resolver.nodeType.isInstance(it)) { "Found archive with descriptor: '$descriptor' but it does not match the expected type of: '${resolver.nodeType.qualifiedName}'" }

            JobResult.Success(it as T)
        } ?: job(JobName("Get archive: '$descriptor'") + CurrentArchive(descriptor)) {
            val metadata = (path resolve resolver.pathForDescriptor(descriptor, "archive-metadata", "json"))
                .takeIf(Files::exists)
                ?.let {
                    ObjectMapper().registerModule(KotlinModule.Builder().build())
                        .readValue<CacheableArchiveInfo>(it.readBytes())
                } ?: fail(ArchiveException.ArchiveNotCached(descriptor.name, trace))

            val data = ArchiveData(
                descriptor,
                metadata.resources.associateWith {
                    val (classifier, extension) = it.split(".")
                    (path resolve resolver.pathForDescriptor(descriptor, classifier, extension))
                        .let(::CachedArchiveResource)
                },
                metadata.children.map {
                    ArchiveChild(
                        it.resolver,
                        resolvers
                            .get(it.resolver)
                            ?.deserializeDescriptor(it.descriptor)
                            ?.attempt() ?: fail(
                            ArchiveException.IllegalState(
                                "Failed to find resolver: '${resolver.name}' when parsing descriptor for child: '${it.descriptor}'",
                                trace
                            )
                        )
                    )
                }
            )

            try {
                resolver.load(data, object : ChildResolver {
                    override suspend fun <T : ArtifactMetadata.Descriptor, N : ArchiveNode<N>> load(
                        descriptor: T,
                        resolver: ArchiveNodeResolver<T, *, N, *, *, *>
                    ): N {
                        return getInternal(descriptor, trace, resolver).orThrow()
                    }
                }).attempt().also {
                    fun addAll(n: T) {
                        mutable[n.descriptor] = n
                        n.children.forEach(::addAll)
                    }

                    addAll(it)
                }
            } catch (e: ArchiveException) {
                fail(e)
            }
        }
    }

    private data class CacheableArchiveInfo(
        val resources: List<String>,
        val children: List<CacheableChildInfo>
    )

    private data class CacheableChildInfo(
        val resolver: String,
        val descriptor: Map<String, String>
    )

    // Not thread safe
    private val beingResolved: MutableSet<ArtifactMetadata.Descriptor> = HashSet()
    private suspend fun <
            D : ArtifactMetadata.Descriptor,
            T : ArtifactRequest<D>,
            R : RepositorySettings,
            RStub : RepositoryStub> cacheInternal(
        request: T,
        repository: R,
        resolver: ArchiveNodeResolver<D, T, *, R, *, *>
    ): JobResult<Unit, ArchiveException> = withContext(CurrentArchive(request.descriptor)) {
        checkRegistration(resolver)
        if (coroutineContext[ArchiveTrace]?.isCircular() == true) return@withContext JobResult.Failure(
            ArchiveException.CircularArtifactException(
                coroutineContext[ArchiveTrace]!!
            )
        )

        val descriptor by request::descriptor

        val metadataPath = path resolve resolver.pathForDescriptor(descriptor, "archive-metadata", "json")
        if (Files.exists(metadataPath))
            return@withContext JobResult.Success(Unit)
        if (!beingResolved.add(descriptor))
            return@withContext JobResult.Success(Unit)

        val result = job(JobName("Cache archive: '${request.descriptor}'") + CurrentArchive(request.descriptor)) {
            val trace = jobElement(ArchiveTrace)

            @Suppress(CAST)
            val factory =
                resolver.factory as RepositoryFactory<
                        R,
                        T,
                        ArtifactStub<T, RStub>,
                        ArtifactReference<*, ArtifactStub<T, RStub>>,
                        ArtifactRepository<T, ArtifactStub<T, RStub>, ArtifactReference<*, ArtifactStub<T, RStub>>>>

            val context: ResolutionContext<T, ArtifactStub<T, RStub>, ArtifactReference<*, ArtifactStub<T, RStub>>> =
                factory.createContext(repository)

            val reference =
                context.repositoryContext.artifactRepository.get(request)
                    .asOutput()
                    .mapFailure { ArchiveException.ArtifactResolutionException(it, trace) }.attempt()

            if (!resolver.metadataType.isInstance(reference.metadata)) fail(
                ArchiveException.IllegalState(
                    "Failed to produce appropriate artifact metadata when evaluating archive: '${request.descriptor}' under resolver: '${resolver.name}'",
                    trace
                )
            )

            val result = (resolver as ArchiveNodeResolver<*, T, *, R, RStub, ArtifactMetadata<*, *>>)
                .cache(reference, object : ArchiveCacheHelper<RStub, R> {
                    override suspend fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, R : RepositorySettings, LocalRStub : RepositoryStub> cache(
                        request: T,
                        repository: R,
                        resolver: ArchiveNodeResolver<D, T, *, R, LocalRStub, *>
                    ): JobResult<ArchiveChild<D>, ArchiveException> {
                        return cacheInternal<D, T, R, LocalRStub>(
                            request,
                            repository,
                            /*cacheInternal@*///trace.child(request.descriptor)
                            resolver
                        ).map {
                            ArchiveChild(
                                resolver.name, request.descriptor
                            )
                        }
                    }

                    override suspend fun resolve(stub: RStub): JobResult<R, ArchiveException.ArtifactResolutionException> {
                        return (context.resolverContext.stubResolver.repositoryResolver as RepositoryStubResolver<RStub, R>)
                            .resolve(stub)
                            .asOutput()
                            .mapFailure { ArchiveException.ArtifactResolutionException(it, trace) }
                    }
                }).attempt()

            val metadataResource = CacheableArchiveInfo(
                result.resources.keys.toList(),
                result.children.map {
                    val childResolver =
                        (resolvers.get(it.resolver) as? ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *, *>)
                            ?: fail(ArchiveException.ArchiveTypeNotFound(it.resolver, trace))

                    val childDescriptor = childResolver.serializeDescriptor(it.descriptor)

                    CacheableChildInfo(
                        it.resolver,
                        childDescriptor
                    )
                }).let(ObjectMapper().registerModule(KotlinModule.Builder().build())::writeValueAsBytes)

            metadataPath
                .apply { make() }
                .writeBytes(metadataResource)

            result.resources.forEach { (name, resource) ->
                val (classifier, extension) = name
                    .split(".")
                    .takeIf { it.size == 2 }
                    ?: fail(
                        ArchiveException.IllegalState(
                            "Resource name should be in the format : '<CLASSIFIER>.<TYPE>'. Found: '$name'",
                            trace
                        )
                    )

                resource.resource copyTo (path resolve resolver.pathForDescriptor(descriptor, classifier, extension))
            }

        }
        beingResolved.remove(descriptor)
        result
    }
}

private object ArchiveTraceFactory : JobElementFactory, JobElementKey<ArchiveTraceFactory> {
    override val dependencies: List<JobElementKey<out JobElementFactory>> = listOf()
    override val key: JobElementKey<out JobElementFactory> = ArchiveTraceFactory
    override val name: String = "Archive Trace Factory"

    override fun <T, E> apply(job: Job<T, E>): Job<T, E> {
        return Job {
            val parent = coroutineContext[ArchiveTrace]
            withContext(ArchiveTrace(jobElement(CurrentArchive).descriptor, parent)) {
                job.invoke()
            }
        }
    }

}

private data class CurrentArchive(
    val descriptor: ArtifactMetadata.Descriptor
) : JobElement {
    override val key: JobElementKey<*> = CurrentArchive

    companion object : JobElementKey<CurrentArchive> {
        override val name: String = "Current Element"
    }
}

public data class ArchiveTrace(
    val descriptor: ArtifactMetadata.Descriptor,
    val parent: ArchiveTrace?
) : JobElement {
    override val key: JobElementKey<*> = ArchiveTrace
    public fun child(descriptor: ArtifactMetadata.Descriptor): ArchiveTrace = ArchiveTrace(descriptor, this)

    public fun isCircular(toCheck: List<ArtifactMetadata.Descriptor> = listOf()): Boolean {
        return toCheck.any { it == descriptor } || parent?.isCircular(toCheck + descriptor) == true
    }

    public fun toList(): List<ArtifactMetadata.Descriptor> {
        return (parent?.toList() ?: listOf()) + descriptor
    }

    override fun toString(): String {
        return toList().joinToString(separator = " -> ") { it.toString() }
    }

    public companion object : JobElementKey<ArchiveTrace> {
        override val name: String = "Archive Trace"
    }
}

public interface ChildResolver {
    public suspend fun <T : ArtifactMetadata.Descriptor, N : ArchiveNode<N>> load(
        descriptor: T,
        resolver: ArchiveNodeResolver<T, *, N, *, *, *>
    ): N
}

public interface ArchiveCacheHelper<RStub : RepositoryStub, RSettings : RepositorySettings> {
    public suspend fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, R : RepositorySettings, LocalRStub : RepositoryStub> cache(
        request: T,
        repository: R,
        resolver: ArchiveNodeResolver<D, T, *, R, LocalRStub, *>
    ): JobResult<ArchiveChild<D>, ArchiveException>

    public suspend fun resolve(stub: RStub): JobResult<RSettings, ArchiveException.ArtifactResolutionException>
}

public interface ArchiveNodeResolver<
        K : ArtifactMetadata.Descriptor,
        R : ArtifactRequest<K>,
        V : ArchiveNode<V>,
        S : RepositorySettings,
        RStub : RepositoryStub,
        M : ArtifactMetadata<K, *>,
        > {
    public val name: String

    public val nodeType: KClass<V>
    public val metadataType: KClass<M>

    public val factory: RepositoryFactory<S, R, *, ArtifactReference<M, *>, *>

    public fun serializeDescriptor(
        descriptor: K
    ): Map<String, String>

    public suspend fun deserializeDescriptor(
        descriptor: Map<String, String>
    ): JobResult<K, ArchiveException>

    // A relative path, not absolute
    public fun pathForDescriptor(
        descriptor: K,
        classifier: String,
        type: String
    ): Path

    public suspend fun load(
        data: ArchiveData<K, CachedArchiveResource>,
        resolver: ChildResolver
    ): JobResult<V, ArchiveException>

    public suspend fun cache(
        ref: ArtifactReference<M, ArtifactStub<R, RStub>>,
        helper: ArchiveCacheHelper<RStub, S>
    ): JobResult<ArchiveData<K, CacheableArchiveResource>, ArchiveException>
}

public suspend fun ArchiveNodeResolver<*, *, *, *, *, *>.trace(): ArchiveTrace = coroutineScope {
    jobElement(ArchiveTrace)
}


public sealed interface ArchiveResource

public class CacheableArchiveResource(
    public val resource: SafeResource
) : ArchiveResource

public data class CachedArchiveResource(
    public val path: Path
) : ArchiveResource

public data class ArchiveData<K : ArtifactMetadata.Descriptor, T : ArchiveResource>(
    val descriptor: K,
    // Keys expected to be in <classifier>.<extension> format, ie 'more-info.json'
    val resources: Map<String, T>,
    //When caching: descriptor, resolver, repository, extra metadata
    //When loading: descriptor, resolver, extra metadata
    val children: List<ArchiveChild<*>>
)

public data class ArchiveChild<T : ArtifactMetadata.Descriptor> internal constructor(
    val resolver: String,
    val descriptor: T
)