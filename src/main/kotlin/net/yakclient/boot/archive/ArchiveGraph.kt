package net.yakclient.boot.archive

import arrow.core.Either
import arrow.core.getOrHandle
import asOutput
import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.*
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.logging.LogLevel
import com.durganmcbroom.jobs.logging.logger
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.*
import net.yakclient.boot.API_VERSION
import net.yakclient.boot.loader.ArchiveClassProvider
import net.yakclient.boot.loader.ArchiveResourceProvider
import net.yakclient.boot.util.ensure
import net.yakclient.common.util.*
import net.yakclient.common.util.resource.LocalResource
import net.yakclient.common.util.resource.SafeResource
import net.yakclient.`object`.MutableObjectContainer
import net.yakclient.`object`.ObjectContainerImpl
import orThrow
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

public open class ArchiveGraph(
    path: Path,
    private val mutable: MutableMap<ArtifactMetadata.Descriptor, ArchiveNode<*>> = HashMap()
) : Map<ArtifactMetadata.Descriptor, ArchiveNode<*>> by mutable {
    private val resolvers: MutableObjectContainer<ArchiveNodeResolver<*, *, *, *, *>> = ObjectContainerImpl()

    public val path: Path = path resolve API_VERSION


    // Public API
    public fun registerResolver(resolver: ArchiveNodeResolver<*, *, *, *, *>) {
        resolvers.register(resolver.name, resolver)
    }

    public suspend fun <K : ArtifactMetadata.Descriptor, T : ArchiveNode<T>> get(
        descriptor: K,
        resolver: ArchiveNodeResolver<K, *, T, *, *>
    ): JobResult<T, ArchiveException> = withContext(ArchiveTraceFactory) {
        getInternal(descriptor, resolver)
    }

    private fun checkRegistration(resolver: ArchiveNodeResolver<*, *, *, *, *>) {
        if (resolvers.has(resolver.name)) return
        registerResolver(resolver)
    }

    private suspend fun <K : ArtifactMetadata.Descriptor, T : ArchiveNode<T>> getInternal(
        descriptor: K,
        resolver: ArchiveNodeResolver<K, *, T, *, *>,
    ): JobResult<T, ArchiveException> = job(JobName("Get archive: '$descriptor'") + CurrentArchive(descriptor)) {
        checkRegistration(resolver)

        mutable[descriptor]?.let {
            if (!resolver.nodeType.isInstance(it)) fail(
                ArchiveException.IllegalState(
                    "Found archive with descriptor: '$descriptor' but it does not match the expected type of: '${resolver.nodeType.name}'",
                    coroutineContext[ArchiveTrace]!!
                )
            )

            it as T
        } ?: run {
            val trace = jobElement(ArchiveTrace)

            logger.log(LogLevel.INFO, "Loading archive: '$descriptor'")

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
                metadata.parents.map {
                    ArchiveParent(
                        resolver
                            .deserializeDescriptor(it.descriptor)
                            .attempt()
                    )
                }
            )

            try {
                resolver.load(data,
                    object : ResolutionHelper {
                        override suspend fun <T : ArtifactMetadata.Descriptor, N : ArchiveNode<N>> load(
                            descriptor: T,
                            resolver: ArchiveNodeResolver<T, *, N, *, *>
                        ): N {
                            return getInternal(
                                descriptor,
                                resolver,
                            ).orThrow()
                        }

                        override suspend fun <T : ArtifactMetadata.Descriptor, N : ArchiveNode<N>> getResolver(
                            name: String,
                            descType: Class<T>,
                            nodeType: Class<N>,
                        ): JobResult<ArchiveNodeResolver<T, *, N, *, *>, ArchiveException> = jobScope {
                            val thisResolver =
                                resolvers.get(name) ?: fail(ArchiveException.ArchiveTypeNotFound(name, trace))
                            ensure(nodeType.isAssignableFrom(thisResolver.nodeType)) {
                                ArchiveException.IllegalState(
                                    "'$name' resolvers node type: '${thisResolver.nodeType.name}' is not assignable from expected: '${nodeType.name}'.",
                                    trace
                                )
                            }

                            thisResolver as ArchiveNodeResolver<T, *, N, *, *>
                        }

                        override fun newAccessTree(scope: ResolutionHelper.AccessTreeScope.() -> Unit): ArchiveAccessTree {
                            val allTargets = ArrayList<ArchiveTarget>()

                            val scopeObject = object : ResolutionHelper.AccessTreeScope {
                                override fun direct(dependency: ArchiveNode<*>) {
                                    val directTarget = ArchiveTarget(
                                        dependency.descriptor,
                                        ArchiveRelationship.Direct(
                                            ArchiveClassProvider(dependency.archive),
                                            ArchiveResourceProvider(dependency.archive),
                                        )
                                    )

                                    val transitiveTargets = dependency.access.targets.map {
                                        ArchiveTarget(
                                            it.descriptor,
                                            ArchiveRelationship.Transitive(
                                                it.relationship.classes,
                                                it.relationship.resources,
                                            )
                                        )
                                    }

                                    allTargets.add(directTarget)
                                    allTargets.addAll(transitiveTargets)
                                }

                                override fun rawTarget(target: ArchiveTarget) {
                                    allTargets.add(target)
                                }
                            }
                            scopeObject.scope()

                            val preliminaryTree: ArchiveAccessTree = object : ArchiveAccessTree {
                                override val descriptor: ArtifactMetadata.Descriptor = data.descriptor
                                override val targets: Set<ArchiveTarget> = allTargets.toSet()
                            }

                            return resolver.auditor.audit(preliminaryTree)
                        }
                    }).attempt().also {
                    fun addAll(n: T) {
                        mutable[n.descriptor] = n
                        n.parents.forEach(::addAll)
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
        val parents: List<CacheableParentInfo>
    )

    private data class CacheableParentInfo(
        val resolver: String,
        val descriptor: Map<String, String>
    )

    private fun <D : ArtifactMetadata.Descriptor> isCached(
        descriptor: D,
        resolver: ArchiveNodeResolver<D, *, *, *, *>
    ): Boolean {
        if (contains(descriptor)) {
            return true
        }

        val metadataPath = path resolve resolver.pathForDescriptor(descriptor, "archive-metadata", "json")

        return Files.exists(metadataPath)
    }

    public suspend fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, R : RepositorySettings> cache(
        request: T,
        repository: R,
        resolver: ArchiveNodeResolver<D, T, *, R, *>
    ): JobResult<Unit, ArchiveException> {
        if (isCached(request.descriptor, resolver)) return JobResult.Success(Unit)

        return job(JobName("Resolve artifact: '${request.descriptor.name}'")) {
            @Suppress(CAST)
            val factory =
                resolver.factory as RepositoryFactory<
                        R,
                        T,
                        ArtifactStub<T, *>,
                        ArtifactReference<*, ArtifactStub<T, *>>,
                        ArtifactRepository<T, ArtifactStub<T, *>, ArtifactReference<*, ArtifactStub<T, *>>>>

            val context: ResolutionContext<T, ArtifactStub<T, *>, ArtifactReference<*, ArtifactStub<T, *>>> =
                factory.createContext(repository)

            logger.log(LogLevel.INFO, "Building artifact tree for: '${request.descriptor}'...")

            val artifact = context.getAndResolve(request)
                .asOutput()
                .mapFailure {
                    ArchiveException.ArtifactResolutionException(
                        it,
                        ArchiveTrace(request.descriptor, null)
                    )
                }
                .attempt()

            logger.log(LogLevel.INFO, "Caching archive tree:")

            val alreadyPrinted = HashSet<ArtifactMetadata.Descriptor>()

            fun logTree(artifact: Artifact, prefix: String, isLast: Boolean) {
                val hasntSeenBefore = alreadyPrinted.add(artifact.metadata.descriptor)
                logger.log(
                    LogLevel.INFO, prefix
                            + (if (isLast) "\\---" else "+---")
                            + " "
                            + artifact.metadata.descriptor.name
                            + (if (!hasntSeenBefore) "***" else "")
                )

                if (hasntSeenBefore) artifact.children
                    .withIndex()
                    .forEach { (index, it) ->
                        val childIsLast = artifact.children.lastIndex == index
                        val newPrefix = prefix + (if (isLast)
                            "    "
                        else "|   ") + " "

                        when (it) {
                            is Either.Left -> logger.log(
                                LogLevel.WARNING,
                                newPrefix + (if (childIsLast) "\\---" else "+---") + " <STUB> " + it.value.request.descriptor
                            )

                            is Either.Right -> logTree(it.value, newPrefix, childIsLast)
                        }
                    }
            }
            logTree(artifact, "", true)

            withContext(ArchiveTraceFactory) {
                cacheInternal(
                    artifact,
                    resolver
                ).attempt()
            }
        }
    }

    private val beingResolved: MutableSet<ArtifactMetadata.Descriptor> = CopyOnWriteArraySet()
    private suspend fun <
            D : ArtifactMetadata.Descriptor,
            T : ArtifactRequest<D>,
            R : RepositorySettings,
//            RStub : RepositoryStub
            > cacheInternal(
//        request: T,
//        repository: R,
        artifact: Artifact,
        resolver: ArchiveNodeResolver<D, T, *, R, *>
    ): JobResult<Unit, ArchiveException> = withContext(CurrentArchive(artifact.metadata.descriptor)) {
        checkRegistration(resolver)

        if (coroutineContext[ArchiveTrace]?.isCircular() == true) return@withContext JobResult.Failure(
            ArchiveException.CircularArtifactException(
                coroutineContext[ArchiveTrace]!!
            )
        )

        if (!resolver.metadataType.isInstance(artifact.metadata)) {
            return@withContext JobResult.Failure(
                ArchiveException.IllegalState(
                    "Invalid metadata type for artifact: '$artifact', expected the entire tree to be of type: '${resolver.metadataType::class.jvmName}'",
                    jobElement(ArchiveTrace)
                )
            )
        }
        val metadata = artifact.metadata as ArtifactMetadata<D, *>

        val descriptor by metadata::descriptor

        val metadataPath = path resolve resolver.pathForDescriptor(descriptor, "archive-metadata", "json")

        if (isCached(descriptor, resolver)) return@withContext JobResult.Success(Unit)

        if (!beingResolved.add(descriptor))
            return@withContext JobResult.Success(Unit)

        val result = job(JobName("Cache archive: '${descriptor}'") + CurrentArchive(descriptor)) {
            val trace = jobElement(ArchiveTrace)


//            val parents = artifact.children.map { it.asOutput() }.mapNotFailure()
//                .mapFailure {
//                    ArchiveException.ArtifactResolutionException(
//                        ArtifactException.ArtifactNotFound(
//                            it.request.descriptor,
//                            it.candidates.map(RepositoryStub::toString)
//                        ),
//                        trace
//                    )
//                }.attempt()

            val result = (resolver as ArchiveNodeResolver<D, T, *, R, ArtifactMetadata<D, *>>)
                .cache(
                    metadata,
                    object : ArchiveCacheHelper<D> {
                        private var resources: MutableMap<String, CacheableArchiveResource> = HashMap()
                        override suspend fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, R : RepositorySettings> cache(
                            request: T,
                            repository: R,
                            resolver: ArchiveNodeResolver<D, T, *, R, *>
                        ): JobResult<ArchiveParent<D>, ArchiveException> {
                            // Starts a new caching job.


                            val originalContext = coroutineContext
                                .minusKey(ArchiveTrace)
                                .minusKey(ArchiveTraceFactory)
                                .minusKey(CurrentArchive)

                            val job = CoroutineScope(originalContext).async {
                                runBlocking(originalContext) {
                                    this@ArchiveGraph.cache(
                                        request, repository, resolver
                                    ).map {
                                        ArchiveParent(
                                            request.descriptor
                                        )
                                    }
                                }
                            }

                            return job.await()
                        }

                        override suspend fun <T : ArtifactRequest<*>, R : RepositorySettings> getResolver(
                            name: String,
                            requestType: Class<T>,
                            repositoryType: Class<R>
                        ): JobResult<ArchiveNodeResolver<*, T, *, R, *>, ArchiveException> = jobScope {
                            val thisResolver =
                                resolvers.get(name) ?: fail(ArchiveException.ArchiveTypeNotFound(name, trace))

                            thisResolver as ArchiveNodeResolver<*, T, *, R, *>
                        }

                        override fun withResource(name: String, resource: SafeResource) {
                            resources[name] = CacheableArchiveResource(resource)
                        }

                        override fun newData(descriptor: D): ArchiveData<D, CacheableArchiveResource> {
                            return ArchiveData(
                                descriptor,
                                resources,
                                artifact.children.map { child ->
                                    val childDescriptor = child.map { it.metadata.descriptor }.getOrHandle { it.request.descriptor } as D

                                    ArchiveParent(
                                        childDescriptor
                                    )
                                }
                            )
                        }
                    }
                ).attempt()

            for (child in artifact.children) {
                val childDescriptor = child.map { it.metadata.descriptor }.getOrHandle { it.request.descriptor } as D

                if (isCached(childDescriptor, resolver)) continue

                cacheInternal(child.asOutput().mapFailure {
                    ArchiveException.ArtifactResolutionException(
                        ArtifactException.ArtifactNotFound(
                            it.request.descriptor,
                            it.candidates.map(RepositoryStub::toString)
                        ),
                        trace
                    )
                }.attempt(), resolver).attempt()
            }

            val metadataResource = CacheableArchiveInfo(
                result.resources.keys.toList(),
                result.parents.map {
//                    val childResolver = resolver
//                        (resolvers.get(it.resolver) as? ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>)
//                            ?: fail(ArchiveException.ArchiveTypeNotFound(it.resolver, trace))

                    val parentDescriptor = resolver.serializeDescriptor(it.descriptor)

                    CacheableParentInfo(
                        resolver.name,
                        parentDescriptor
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

public interface ResolutionHelper {
    public suspend fun <T : ArtifactMetadata.Descriptor, N : ArchiveNode<N>> load(
        descriptor: T,
        resolver: ArchiveNodeResolver<T, *, N, *, *>
    ): N

    public suspend fun <T : ArtifactMetadata.Descriptor, N : ArchiveNode<N>> getResolver(
        name: String,
        descType: Class<T>,
        nodeType: Class<N>,
    ): JobResult<ArchiveNodeResolver<T, *, N, *, *>, ArchiveException>

    public fun newAccessTree(scope: AccessTreeScope.() -> Unit): ArchiveAccessTree

    public interface AccessTreeScope {
        public fun direct(dependency: ArchiveNode<*>)

        public fun allDirect(dependencies: Collection<ArchiveNode<*>>) {
            for (d in dependencies) direct(d)
        }

        public fun rawTarget(
            target: ArchiveTarget
        )
    }
}

public suspend fun <T : ArtifactMetadata.Descriptor, N : ArchiveNode<N>> ResolutionHelper.getResolver(
    name: String,
    descType: KClass<T>,
    nodeType: KClass<N>,
): JobResult<ArchiveNodeResolver<T, *, N, *, *>, ArchiveException> {
    return getResolver(name, descType.java, nodeType.java)
}

context(nodeResolver@ ArchiveNodeResolver<D, *, N, *, *>)
public suspend fun <D : ArtifactMetadata.Descriptor, N : ArchiveNode<N>> ResolutionHelper.load(parent: ArchiveParent<D>): N {
    return load(
        parent.descriptor,
        this@nodeResolver,
    )
}

public interface ArchiveCacheHelper<K : ArtifactMetadata.Descriptor> {
    public suspend fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, R : RepositorySettings> cache(
        request: T,
        repository: R,
        resolver: ArchiveNodeResolver<D, T, *, R, *>
    ): JobResult<ArchiveParent<D>, ArchiveException>

    public suspend fun <T : ArtifactRequest<*>, R : RepositorySettings> getResolver(
        name: String,
        requestType: Class<T>,
        repositoryType: Class<R>,
    ): JobResult<ArchiveNodeResolver<*, T, *, R, *>, ArchiveException>
//
//    public suspend fun resolve(stub: RStub): JobResult<RSettings, ArchiveException.ArtifactResolutionException>


    public fun withResource(name: String, resource: SafeResource)

    public fun withResources(resources: Map<String, SafeResource>) {
        resources.forEach {
            withResource(it.key, it.value)
        }
    }

    public fun newData(descriptor: K): ArchiveData<K, CacheableArchiveResource>
}

public fun ArchiveCacheHelper<*>.withResource(name: String, resource: SafeResource?) {
    if (resource == null) return
    withResource(name, resource)
}

public interface ArchiveNodeResolver<
        K : ArtifactMetadata.Descriptor,
        R : ArtifactRequest<K>,
        V : ArchiveNode<V>,
        S : RepositorySettings,
        M : ArtifactMetadata<K, *>> {
    public val name: String

    public val nodeType: Class<in V>
    public val metadataType: Class<M>

    public val factory: RepositoryFactory<S, R, *, ArtifactReference<M, *>, *>

    public val auditor: ArchiveAccessAuditor
        get() = ArchiveAccessAuditor { tree -> tree }
//    public val auditors: List<ArchiveAccessAuditor<K>>
//        get() = listOf()

    public fun serializeDescriptor(
        descriptor: K
    ): Map<String, String>

    public suspend fun deserializeDescriptor(
        descriptor: Map<String, String>
    ): JobResult<K, ArchiveException>

    // Will be resolved against the base directory, this means absolute paths can technically
    // work but for most cases, just use a relative path (not starting with /)
    public fun pathForDescriptor(
        descriptor: K,
        classifier: String,
        type: String
    ): Path

    public suspend fun load(
        data: ArchiveData<K, CachedArchiveResource>,
//        accessTree: ArchiveAccessTree<K>,
        helper: ResolutionHelper
    ): JobResult<V, ArchiveException>

    public suspend fun cache(
//        ref: ArtifactReference<M, ArtifactStub<R, RStub>>,
        metadata: M,
//        children: List<K>,
        helper: ArchiveCacheHelper<K>
    ): JobResult<ArchiveData<K, CacheableArchiveResource>, ArchiveException>
}

public suspend fun ArchiveNodeResolver<*, *, *, *, *>.trace(): ArchiveTrace = coroutineScope {
    jobElement(ArchiveTrace)
}

//public data class ArchiveDataTree<K : ArtifactMetadata.Descriptor, T : ArchiveResource>(
//    val data: ArchiveData<K, T>,
//    val children: List<ArchiveDataTree<K, T>>
//)

public sealed interface ArchiveResource

public class CacheableArchiveResource(
    public val resource: SafeResource
) : ArchiveResource

public data class CachedArchiveResource(
    public val path: Path
) : ArchiveResource

public data class ArchiveData<K : ArtifactMetadata.Descriptor, T : ArchiveResource> internal constructor(
    val descriptor: K,
    // Keys expected to be in <classifier>.<extension> format, ie 'more-info.json'
    val resources: Map<String, T>,
    //When caching: descriptor, resolver, repository, extra metadata
    //When loading: descriptor, resolver, extra metadata
    val parents: List<ArchiveParent<K>>
)

public data class ArchiveParent<T : ArtifactMetadata.Descriptor> internal constructor(
    val descriptor: T
)