package net.yakclient.boot.archive

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.*
import com.durganmcbroom.jobs.logging.LogLevel
import com.durganmcbroom.jobs.logging.info
import com.durganmcbroom.jobs.logging.logger
import com.durganmcbroom.resources.LocalResource
import com.durganmcbroom.resources.Resource
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import net.yakclient.boot.API_VERSION
import net.yakclient.boot.loader.ArchiveClassProvider
import net.yakclient.boot.loader.ArchiveResourceProvider
import net.yakclient.common.util.copyTo
import net.yakclient.common.util.make
import net.yakclient.common.util.resolve
import net.yakclient.`object`.MutableObjectContainer
import net.yakclient.`object`.ObjectContainerImpl
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArraySet
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

    public fun <K : ArtifactMetadata.Descriptor, T : ArchiveNode<T>> get(
        descriptor: K,
        resolver: ArchiveNodeResolver<K, *, T, *, *>
    ): Job<T> = Job {
        withContext(ArchiveTraceFactory) {
            getInternal(descriptor, resolver)().mapException {
                ArchiveException(ArchiveTrace(descriptor, null), "An error occurred while loading archive: '$descriptor'", it)
            }
        }
    }

    private fun checkRegistration(resolver: ArchiveNodeResolver<*, *, *, *, *>) {
        if (resolvers.has(resolver.name)) return
        registerResolver(resolver)
    }

    private fun <K : ArtifactMetadata.Descriptor, T : ArchiveNode<T>> getInternal(
        descriptor: K,
        resolver: ArchiveNodeResolver<K, *, T, *, *>,
    ): Job<T> = job(JobName("Get archive: '$descriptor'") + CurrentArchive(descriptor)) {
        checkRegistration(resolver)

        mutable[descriptor]?.let {
            if (!resolver.nodeType.isInstance(it)) throw ArchiveException(
                trace(),
                "Found archive with descriptor: '$descriptor' but it does not match the expected type of: '${resolver.nodeType.name}'",
            )

            it as T
        } ?: run {
            val trace = trace()

            info("Loading archive: '$descriptor'")

            val metadata = (path resolve resolver.pathForDescriptor(descriptor, "archive-metadata", "json"))
                .takeIf(Files::exists)
                ?.let {
                    ObjectMapper().registerModule(KotlinModule.Builder().build())
                        .readValue<CacheableArchiveInfo>(it.readBytes())
                } ?: throw ArchiveException.ArchiveNotCached(descriptor.name, trace)

            val data = ArchiveData(
                descriptor,
                metadata.resources.mapValues { (_, resourcePath) ->
                    // Resource path may be absolute, in this case resolve just returns it.
                    (path resolve resourcePath).let(::CachedArchiveResource)
                },
                metadata.parents.map {
                    ArchiveParent(
                        resolver
                            .deserializeDescriptor(it.descriptor, trace)
                            .merge()
                    )
                }
            )

            resolver.load(data,
                object : ResolutionHelper {
                    override fun <T : ArtifactMetadata.Descriptor, N : ArchiveNode<N>> load(
                        descriptor: T,
                        resolver: ArchiveNodeResolver<T, *, N, *, *>
                    ): N {
                        return getInternal(
                            descriptor,
                            resolver,
                        )().merge()
                    }

                    override fun <T : ArtifactMetadata.Descriptor, N : ArchiveNode<N>> getResolver(
                        name: String,
                        descType: Class<T>,
                        nodeType: Class<N>
                    ): Result<ArchiveNodeResolver<T, *, N, *, *>> = result {
                        val thisResolver =
                            resolvers.get(name) ?: throw ArchiveException.ArchiveTypeNotFound(name, trace)

                        if (!nodeType.isAssignableFrom(thisResolver.nodeType)) throw ArchiveException(
                            trace,
                            "'$name' resolvers node type: '${thisResolver.nodeType.name}' is not assignable from expected: '${nodeType.name}'.",
                        )

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
                })().merge().also {
                fun addAll(n: T) {
                    mutable[n.descriptor] = n
                    n.parents.forEach(::addAll)
                }

                addAll(it)
            }
        }
    }

    private data class CacheableArchiveInfo(
        // Name of resource to the path
        val resources: Map<String, String>,
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

    public fun <
            D : ArtifactMetadata.Descriptor,
            T : ArtifactRequest<D>,
            R : RepositorySettings,
            M : ArtifactMetadata<D, *>> cache(
        request: T,
        repository: R,
        resolver: ArchiveNodeResolver<D, T, *, R, M>
    ): Job<Unit> {
        val descriptor = request.descriptor
        if (isCached(descriptor, resolver)) return SuccessfulJob { Unit }

        return job(JobName("Resolve artifact: '${descriptor.name}'")) {
            val context = resolver.createContext(repository)

            logger.log(LogLevel.INFO, "Building artifact tree for: '$descriptor'...")

            val artifact = context.getAndResolve(request)().merge()

            logger.log(LogLevel.INFO, "Caching archive tree:")

            printTree(artifact)().merge()

            withContext(ArchiveTraceFactory) {
                cacheInternal(
                    artifact,
                    resolver
                )().merge()
            }
        }.mapException {
            ArchiveException(ArchiveTrace(descriptor,null), "An error occurred while caching archive: '$descriptor'", it)
        }
    }

    private val beingResolved: MutableSet<ArtifactMetadata.Descriptor> = CopyOnWriteArraySet()
    private fun <
            D : ArtifactMetadata.Descriptor,
            T : ArtifactRequest<D>,
            R : RepositorySettings,
            M : ArtifactMetadata<D, *>,
            > cacheInternal(
        artifact: Artifact<M>,
        resolver: ArchiveNodeResolver<D, T, *, R, M>
    ): Job<Unit> =
        job(JobName("Cache archive: '${artifact.metadata.descriptor}'") + CurrentArchive(artifact.metadata.descriptor)) {
            checkRegistration(resolver)
            val trace = trace()

            if (context[ArchiveTrace]?.isCircular() == true) throw ArchiveException.CircularArtifactException(trace)

            if (!resolver.metadataType.isInstance(artifact.metadata)) throw ArchiveException(
                trace,
                "Invalid metadata type for artifact: '$artifact', expected the entire tree to be of type: '${resolver.metadataType::class.jvmName}'",
            )

            val metadata = artifact.metadata as ArtifactMetadata<D, *>

            val descriptor by metadata::descriptor

            val metadataPath = path resolve resolver.pathForDescriptor(descriptor, "archive-metadata", "json")

            if (isCached(descriptor, resolver)) (Unit)

            if (!beingResolved.add(descriptor)) return@job

            val result = (resolver as ArchiveNodeResolver<D, T, *, R, ArtifactMetadata<D, *>>)
                .cache(
                    metadata,
                    object : ArchiveCacheHelper<D> {
                        private var resources: MutableMap<String, CacheableArchiveResource> = HashMap()
                        override fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, R : RepositorySettings> cache(
                            request: T,
                            repository: R,
                            resolver: ArchiveNodeResolver<D, T, *, R, *>
                        ): Job<ArchiveParent<D>> {
                            // Starts a new caching job.

                            return this@ArchiveGraph.cache(
                                request, repository, resolver
                            ).map {
                                ArchiveParent(
                                    request.descriptor
                                )
                            }
                        }

                        override fun <T : ArtifactRequest<*>, R : RepositorySettings> getResolver(
                            name: String,
                            requestType: Class<T>,
                            repositoryType: Class<R>
                        ): Result<ArchiveNodeResolver<*, T, *, R, *>> = result {
                            val thisResolver =
                                resolvers.get(name) ?: throw ArchiveException.ArchiveTypeNotFound(name, trace)

                            thisResolver as ArchiveNodeResolver<*, T, *, R, *>
                        }

                        override fun withResource(name: String, resource: Resource) {
                            resources[name] = CacheableArchiveResource(resource)
                        }

                        override fun newData(descriptor: D): ArchiveData<D, CacheableArchiveResource> {
                            return ArchiveData(
                                descriptor,
                                resources,
                                artifact.children.map { child ->
                                    val childDescriptor = child.metadata.descriptor
                                    ArchiveParent(
                                        childDescriptor
                                    )
                                }
                            )
                        }
                    }
                )().merge()

            for (child: Artifact<M> in artifact.children) {
                val childDescriptor =
                    child.metadata.descriptor //.map { it.metadata.descriptor }.getOrHandle { it.request.descriptor } as D

                if (isCached(childDescriptor, resolver)) continue

                cacheInternal(child, resolver)().merge()
            }

            val resourcePaths = result.resources.map { (name, wrapper) ->
                val (classifier, extension) = name
                    .split(".")
                    .takeIf { it.size == 2 }
                    ?: throw ArchiveException(
                        trace,
                        "Resource name should be in the format : '<CLASSIFIER>.<TYPE>'. Found: '$name'",
                    )

                val path =
                    this@ArchiveGraph.path resolve if (wrapper.resource is LocalResource) Path.of(wrapper.resource.location)
                    else resolver.pathForDescriptor(descriptor, classifier, extension)

                Triple(name, wrapper, path)
            }

            val metadataResource = CacheableArchiveInfo(
                resourcePaths
                    .associate {
                        it.first to it.third.toString()
                    },
                result.parents.map {
                    val parentDescriptor = resolver.serializeDescriptor(it.descriptor)

                    CacheableParentInfo(
                        resolver.name,
                        parentDescriptor
                    )
                }).let(ObjectMapper().registerModule(KotlinModule.Builder().build())::writeValueAsBytes)

            resourcePaths.forEach { (_, wrapper, path) ->
                if (wrapper.resource !is LocalResource) (wrapper.resource copyTo path)
            }

            metadataPath
                .apply { make() }
                .writeBytes(metadataResource)
            beingResolved.remove(descriptor)
            result
        }
}

private object ArchiveTraceFactory : JobFacetFactory, JobContext.Key<ArchiveTraceFactory> {
    override val dependencies: List<JobContext.Key<out JobFacetFactory>> = listOf()
    override val key: JobContext.Key<ArchiveTraceFactory> = ArchiveTraceFactory
    override val name: String = "Archive Trace Factory"

    override fun <T> apply(job: Job<T>, oldContext: JobContext): Job<T> =
        useContextFor(job) {
            val parent = oldContext[ArchiveTrace]

            facetOrNull(CurrentArchive)?.let {
                ArchiveTrace(it.descriptor, parent)
            } ?: parent
            ?: throw IllegalArgumentException("Unable to construct Archive trace from given context facets.")
        }
}

private data class CurrentArchive(
    val descriptor: ArtifactMetadata.Descriptor
) : JobContext.Facet {
    override val key: JobContext.Key<*> = CurrentArchive

    companion object : JobContext.Key<CurrentArchive> {
        override val name: String = "Current Element"
    }
}

public data class ArchiveTrace(
    val descriptor: ArtifactMetadata.Descriptor,
    val parent: ArchiveTrace?
) : JobContext.Facet {
    override val key: JobContext.Key<ArchiveTrace> = ArchiveTrace
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

    public companion object : JobContext.Key<ArchiveTrace> {
        override val name: String = "Archive Trace"
    }
}

public interface ResolutionHelper {
    public fun <T : ArtifactMetadata.Descriptor, N : ArchiveNode<N>> load(
        descriptor: T,
        resolver: ArchiveNodeResolver<T, *, N, *, *>
    ): N

    public fun <T : ArtifactMetadata.Descriptor, N : ArchiveNode<N>> getResolver(
        name: String,
        descType: Class<T>,
        nodeType: Class<N>,
    ): Result<ArchiveNodeResolver<T, *, N, *, *>>

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

public fun <T : ArtifactMetadata.Descriptor, N : ArchiveNode<N>> ResolutionHelper.getResolver(
    name: String,
    descType: KClass<T>,
    nodeType: KClass<N>,
): Result<ArchiveNodeResolver<T, *, N, *, *>> {
    return getResolver(name, descType.java, nodeType.java)
}

context(nodeResolver@ ArchiveNodeResolver<D, *, N, *, *>)
public fun <D : ArtifactMetadata.Descriptor, N : ArchiveNode<N>> ResolutionHelper.load(parent: ArchiveParent<D>): N {
    return load(
        parent.descriptor,
        this@nodeResolver,
    )
}

public interface ArchiveCacheHelper<K : ArtifactMetadata.Descriptor> {
    public fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, R : RepositorySettings> cache(
        request: T,
        repository: R,
        resolver: ArchiveNodeResolver<D, T, *, R, *>
    ): Job<ArchiveParent<D>>

    public fun <T : ArtifactRequest<*>, R : RepositorySettings> getResolver(
        name: String,
        requestType: Class<T>,
        repositoryType: Class<R>,
    ): Result<ArchiveNodeResolver<*, T, *, R, *>>

    public fun withResource(name: String, resource: Resource)

    public fun withResources(resources: Map<String, Resource>) {
        resources.forEach {
            withResource(it.key, it.value)
        }
    }

    public fun newData(descriptor: K): ArchiveData<K, CacheableArchiveResource>
}

public fun ArchiveCacheHelper<*>.withResource(name: String, resource: Resource?) {
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

    public val auditor: ArchiveAccessAuditor
        get() = ArchiveAccessAuditor { tree -> tree }

    public fun createContext(settings: S): ResolutionContext<R, *, M, *>

    public fun serializeDescriptor(
        descriptor: K
    ): Map<String, String>

    public fun deserializeDescriptor(
        descriptor: Map<String, String>,
        trace: ArchiveTrace,
    ): Result<K>

    // Will be resolved against the base directory, this means absolute paths can technically
    // work but for most cases, just use a relative path (not starting with /)
    public fun pathForDescriptor(
        descriptor: K,
        classifier: String,
        type: String
    ): Path

    public fun load(
        data: ArchiveData<K, CachedArchiveResource>,
        helper: ResolutionHelper
    ): Job<V>

    public fun cache(
        metadata: M,
        helper: ArchiveCacheHelper<K>
    ): Job<ArchiveData<K, CacheableArchiveResource>>
}

public fun JobScope.trace(): ArchiveTrace = facet(ArchiveTrace)


public sealed interface ArchiveResource

public class CacheableArchiveResource(
    public val resource: Resource
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