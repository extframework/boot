package dev.extframework.boot.archive

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.JobScope
import com.durganmcbroom.jobs.async.AsyncJob
import com.durganmcbroom.jobs.async.asyncJob
import com.durganmcbroom.jobs.async.mapAsync
import com.durganmcbroom.jobs.logging.info
import com.durganmcbroom.jobs.mapException
import com.durganmcbroom.resources.LocalResource
import com.durganmcbroom.resources.Resource
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.API_VERSION
import dev.extframework.boot.archive.audit.*
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.monad.tag
import dev.extframework.boot.monad.toTree
import dev.extframework.boot.util.textifyTree
import dev.extframework.boot.util.toGraphable
import dev.extframework.common.util.copyTo
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.`object`.MutableObjectContainer
import dev.extframework.`object`.ObjectContainerImpl
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashMap
import kotlin.io.path.writeBytes
import kotlin.reflect.jvm.jvmName

public open class DefaultArchiveGraph(
    path: Path,
    protected val mutable: MutableMap<ArtifactMetadata.Descriptor, ArchiveNode<*>> = HashMap()
) : ArchiveGraph {
    protected val resolvers: MutableObjectContainer<ArchiveNodeResolver<*, *, *, *, *>> = ObjectContainerImpl()

    override val path: Path = path resolve API_VERSION

    /**
     * Register a resolver.
     *
     * @param resolver the resolver.
     */
    override fun registerResolver(resolver: ArchiveNodeResolver<*, *, *, *, *>) {
        resolvers.register(resolver.name, resolver)
    }

    /**
     * Get a registered resolver or null.
     *
     * @param name The name to get by.
     * @return The resolver or null.
     */
    override fun getResolver(name: String): ArchiveNodeResolver<*, *, *, *, *>? {
        return resolvers.get(name)
    }

    /**
     * Looks for a node with the specified descriptor that has already been loaded
     * or null if it was not found.
     *
     * @param descriptor The descriptor to search for.
     * @return The node or null.
     */
    override fun getNode(descriptor: ArtifactMetadata.Descriptor): ArchiveNode<*>? = mutable[descriptor]

    /**
     * Returns a collection of all the nodes in this archive graph.
     *
     * @return All the currently loaded nodes.
     */
    override fun nodes(): Collection<ArchiveNode<*>> = mutable.values

    /**
     * Determines if the given descriptor has been previously cached
     *
     * @param descriptor The descriptor.
     * @param resolver The resolver that loaded the descriptor.
     *
     * @return Whether that archive is cached.
     */
    protected fun <D : ArtifactMetadata.Descriptor> isCached(
        descriptor: D,
        resolver: ArchiveNodeResolver<D, *, *, *, *>
    ): Boolean {
        checkRegistration(resolver)

        if (loaded(descriptor)) {
            return true
        }

        val metadataPath = path resolve resolver.pathForDescriptor(descriptor, "archive-metadata", "json")

        return Files.exists(metadataPath)
    }

    private fun checkRegistration(resolver: ArchiveNodeResolver<*, *, *, *, *>) {
        if (resolvers.has(resolver.name)) return
        registerResolver(resolver)
    }

    /**
     * Default context ot use when auditing.
     */
    private inner class DefaultAuditContext(override val trace: ArchiveTrace, override val graph: ArchiveGraph) :
        AuditContext

    private suspend fun <K : ArtifactMetadata.Descriptor, N : ArchiveNode<K>> JobScope.checkLoaded(
        descriptor: K,
        resolver: ArchiveNodeResolver<K, *, *, *, *>,
        or: suspend JobScope.() -> N
    ): N = getNode(descriptor)?.let { node ->
        check(resolver.nodeType.isInstance(node)) { "Archive node: '$descriptor' was found loaded but it does not match the expected type of: '${resolver.nodeType.name}'. Its type was: '${node::class.java.name}" }

        node as N
    } ?: or()

    /**
     * Get as defined by the supertype.
     *
     * Throws if the [descriptor] cannot be found.
     *
     * Reads the given archive tree from the cache, audits it, then
     * delegates to [getInternal] for loading.
     *
     * @see dev.extframework.boot.archive.audit.ArchiveTreeAuditor
     */
    override fun <K : ArtifactMetadata.Descriptor, T : ArchiveNode<K>> getAsync(
        descriptor: K,
        resolver: ArchiveNodeResolver<K, *, T, *, *>
    ): AsyncJob<T> = asyncJob {
        checkRegistration(resolver)

        checkLoaded(descriptor, resolver) {
            if (!isCached(descriptor, resolver)) throw ArchiveException.ArchiveNotCached(
                descriptor.name, ArchiveTrace(descriptor)
            )

            val archiveTree: Tree<Tagged<ArchiveData<*, CachedArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>> =
                readArchiveTree(
                    descriptor,
                    resolver,
                    ArchiveTrace(descriptor)
                )().merge()

            val auditedTree = resolver.auditors[ArchiveTreeAuditContext::class].audit(
                ArchiveTreeAuditContext(
                    archiveTree,
                    ArchiveTrace(descriptor), this@DefaultArchiveGraph
                )
            )().merge().tree

            info(
                "Archive tree of '$descriptor' after auditing:\n" +
                        textifyTree(auditedTree.toGraphable { it.value.descriptor.name })().merge()
            )

            getInternal(
                auditedTree,
                ArchiveTrace(descriptor)
            )().merge() as T
        }
    }


    /**
     * Recursively reads the given archive tree from the cache. This can throw
     * if not all required resolvers are registered before this is
     * called.
     *
     * @param descriptor The descriptor to start reading from.
     * @param resolver The base resolver.
     * @param trace The base trace as this is a recursive function.
     */
    protected fun <T : ArtifactMetadata.Descriptor> readArchiveTree(
        descriptor: T,
        resolver: ArchiveNodeResolver<T, *, *, *, *>,
        trace: ArchiveTrace
    ): AsyncJob<Tree<Tagged<ArchiveData<*, CachedArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        trace.checkCircularity()

        val metadataPath = path resolve resolver.pathForDescriptor(descriptor, "archive-metadata", "json")
        val info = jacksonObjectMapper().readValue<CacheableArchiveData>(
            metadataPath.toFile()
        )

        val parents = info.access.mapAsync {
            val currResolver = getResolver(it.resolver)
                ?: throw IllegalStateException("Resolver: '${it.resolver}' not found when hydrating cache. Please register it.")

            val currDescriptor = currResolver.deserializeDescriptor(it.descriptor, trace).merge()

            readArchiveTree(
                currDescriptor,
                currResolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>,
                trace.child(currDescriptor)
            )().merge()
        }

        val resources = info.resources.mapValues { (_, value) ->
            CachedArchiveResource(Path.of(value))
        }

        Tree(
            ArchiveData(
                descriptor,
                CachedArchiveResource::class,
                resources
            ).tag(resolver),
            parents.awaitAll()
        )
    }

    /**
     * Given an archive tree performs the necessary operations to load it.
     */
    @Suppress("UNCHECKED_CAST")
    private fun getInternal(
        tree: Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>,
        trace: ArchiveTrace
    ): AsyncJob<ArchiveNode<*>> = asyncJob {
        checkLoaded(
            tree.item.value.descriptor,
            tree.item.tag as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>
        ) {
            val (data, resolver) = tree.item

            val parents = tree.parents.mapAsync {
                getInternal(it, trace.child(it.item.value.descriptor))().merge()
            }.awaitAll()

            val accessTree = object : ArchiveAccessTree {
                override val descriptor: ArtifactMetadata.Descriptor = tree.item.value.descriptor
                override val targets: List<ArchiveTarget> = (parents
                    .map {
                        ArchiveTarget(
                            it.descriptor,
                            ArchiveRelationship.Direct(
                                it
                            )
                        )
                    } + parents
                    .flatMap { it.access.targets }
                    .map {
                        ArchiveTarget(
                            it.descriptor,
                            ArchiveRelationship.Transitive(
                                it.relationship.node
                            )
                        )
                    })
            }

            val auditedTree = tree.item.tag.auditors[ArchiveAccessAuditContext::class].audit(
                ArchiveAccessAuditContext(
                    accessTree,
                    trace,
                    this@DefaultArchiveGraph
                )
            )().merge().tree

            val node = (resolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>)
                .load(
                    (data as ArchiveData<ArtifactMetadata.Descriptor, CachedArchiveResource>),
                    auditedTree,
                    object : ResolutionHelper {
                        override val trace: ArchiveTrace = trace
                    }
                )().merge() as ArchiveNode<ArtifactMetadata.Descriptor>

            mutable[node.descriptor] = node

            node
        }
    }

    /**
     * Caches the given request + repository as defined by the supertype.
     */
    override fun <
            D : ArtifactMetadata.Descriptor,
            T : ArtifactRequest<D>,
            R : RepositorySettings,
            M : ArtifactMetadata<D, ArtifactMetadata.ParentInfo<T, R>>> cacheAsync(
        request: T,
        repository: R,
        resolver: ArchiveNodeResolver<D, T, *, R, M>
    ): AsyncJob<Tree<Tagged<ArchiveData<*, CachedArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        checkRegistration(resolver)

        val descriptor = request.descriptor
        if (isCached(descriptor, resolver)) return@asyncJob readArchiveTree(
            descriptor,
            resolver,
            ArchiveTrace(descriptor, null)
        )().merge()

        val artifactTree = resolveArtifact(request, repository, resolver)().merge()

        cacheInternal(artifactTree, resolver)().merge()
    }

    /**
     * Cacheable Archive info.
     *
     * Resources are stored in a map of: 'identifier defined
     * by the resolver' -> 'absolute path in the file system'.
     */
    private data class CacheableArchiveData(
        // Name of resource to the path
        val resources: Map<String, String>,
        val access: List<CacheableParentInfo>,
    )

    /**
     * Cacheable information pointing to parent archive data.
     */
    private data class CacheableParentInfo(
        val resolver: String,
        val descriptor: Map<String, String>,
    )

    /**
     * Internal caching, constructs the archive tree from the artifact tree and
     * delegates to [cacheInternal]'
     */
    private fun <D : ArtifactMetadata.Descriptor, M : ArtifactMetadata<D, *>> cacheInternal(
        artifactTree: Artifact<M>,
        resolver: ArchiveNodeResolver<D, *, *, *, M>
    ): AsyncJob<Tree<Tagged<ArchiveData<*, CachedArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        val archiveTree =
            constructArchiveTree(artifactTree, resolver, ArchiveTrace(artifactTree.metadata.descriptor))().merge()
                .toTree()

        cacheInternal(archiveTree, ArchiveTrace(artifactTree.metadata.descriptor, null))().merge()
    }

    // Represents all the currently running jobs
    private val beingCached: MutableMap<
            ArtifactMetadata.Descriptor,
            Deferred<Tree<Tagged<ArchiveData<*, CachedArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>>>
            > = ConcurrentHashMap()

    /**
     * Recursively caches the given tree of archive data.
     */
    private fun cacheInternal(
        tree: Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>,
        trace: ArchiveTrace
    ): AsyncJob<Tree<Tagged<ArchiveData<*, CachedArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        coroutineScope {
            trace.checkCircularity()

            val data = tree.item.value
            val resolver = tree.item.tag as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>

            if (isCached(
                    data.descriptor,
                    resolver
                ) || data.resourceType != CacheableArchiveResource::class
            ) {
                return@coroutineScope readArchiveTree(data.descriptor, resolver, trace)().merge()
            }

            data as ArchiveData<*, CacheableArchiveResource>

            beingCached[data.descriptor]?.await()?.let {
                return@coroutineScope it
            }

            val asyncBlock = async {
                val metadataPath = path resolve resolver.pathForDescriptor(data.descriptor, "archive-metadata", "json")

                val resourcePaths = data.resources.map { (name, wrapper) ->
                    val (classifier, extension) = name
                        .split(".")
                        .takeIf { it.size == 2 }
                        ?: throw ArchiveException(
                            trace,
                            "Resource name should be in the format : '<CLASSIFIER>.<TYPE>'. Found: '$name'",
                        )

                    val path =
                        this@DefaultArchiveGraph.path resolve if (wrapper.resource is LocalResource) Path.of(wrapper.resource.location)
                        else resolver.pathForDescriptor(data.descriptor, classifier, extension)

                    Triple(name, wrapper, path)
                }

                val metadataResource = CacheableArchiveData(
                    resourcePaths.associate {
                        it.first to it.third.toString()
                    },
                    tree.parents.map { (it, _) ->
                        val parentDescriptor =
                            (it.tag as? ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>)?.serializeDescriptor(
                                it.value.descriptor
                            )
                                ?: throw IllegalArgumentException("Unknown archive resolver: '$resolver'. Make sure it is registered before you try to cache your archive.")

                        CacheableParentInfo(
                            it.tag.name,
                            parentDescriptor
                        )
                    }
                ).let(ObjectMapper().registerModule(KotlinModule.Builder().build())::writeValueAsBytes)

                val parents = tree.parents.mapAsync {
                    cacheInternal(it, trace)().merge()
                }.awaitAll()

                resourcePaths.mapAsync { (name, wrapper, path) ->
                    if (wrapper.resource !is LocalResource) {
                        info("Retrieving: '${data.descriptor}#$name' from: '${wrapper.resource.location}'")
                        wrapper.resource copyTo path
                    }
                }.awaitAll()

                metadataPath
                    .apply { make() }
                    .writeBytes(metadataResource)

                Tree(
                    ArchiveData(
                        tree.item.value.descriptor,
                        CachedArchiveResource::class,
                        resourcePaths.associate {
                            it.first to CachedArchiveResource(it.third)
                        }
                    ) tag resolver, parents)
            }

            beingCached[data.descriptor] = asyncBlock

            val result = asyncBlock.await()

            beingCached.remove(data.descriptor)?.join()

            result
        }
    }

    /**
     * Resolves the requests artifact and maps NotFound exceptions
     * to [ArchiveException.ArchiveNotFound]
     */
    private fun <
            D : ArtifactMetadata.Descriptor,
            T : ArtifactRequest<D>,
            R : RepositorySettings,
            M : ArtifactMetadata<D, ArtifactMetadata.ParentInfo<T, R>>> resolveArtifact(
        request: T,
        repository: R,
        resolver: ArchiveNodeResolver<D, T, *, R, M>,
    ): AsyncJob<Artifact<M>> {
        checkRegistration(resolver)

        val descriptor = request.descriptor

        return asyncJob(JobName("Resolve artifact: '${descriptor.name}'")) {
            val context = resolver.createContext(repository)

            val artifact = context.getAndResolveAsync(request)().mapException {
                if (it is ArtifactException.ArtifactNotFound) ArchiveException.ArchiveNotFound(
                    ArchiveTrace(request.descriptor, null),
                    it.desc,
                    it.searchedIn
                ) else ArchiveException(ArchiveTrace(request.descriptor, null), null, it)
            }.merge()

            info("Artifact tree for: '$descriptor'...\n" + textifyTree(artifact.toGraphable())().merge())

            artifact
        }
    }

    /**
     * Builds a [ArchiveData] tree from an [Artifact].
     */
    protected fun <
            D : ArtifactMetadata.Descriptor,
            M : ArtifactMetadata<D, *>,
            > constructArchiveTree(
        artifact: Artifact<M>,
        resolver: ArchiveNodeResolver<D, *, *, *, M>,
        trace: ArchiveTrace,
    ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>> =
        asyncJob(
            JobName("Construct archive tree: '${artifact.metadata.descriptor}'")
        ) {
            checkRegistration(resolver)

            if (context[ArchiveTrace]?.isCircular() == true) throw ArchiveException.CircularArtifactException(trace)

            if (!resolver.metadataType.isInstance(artifact.metadata)) throw ArchiveException(
                trace,
                "Invalid metadata type for artifact: '$artifact', expected the entire tree to be of type: '${resolver.metadataType::class.jvmName}'",
            )

            (resolver as ArchiveNodeResolver<D, *, *, *, ArtifactMetadata<D, *>>)
                .cache(
                    artifact as Artifact<ArtifactMetadata<D, *>>,
                    object : CacheHelper<D> {
                        private var resources: MutableMap<String, CacheableArchiveResource> = HashMap()

                        override val trace: ArchiveTrace = trace

                        override fun <D : ArtifactMetadata.Descriptor, M : ArtifactMetadata<D, *>> cache(
                            artifact: Artifact<M>,
                            resolver: ArchiveNodeResolver<D, *, *, *, M>
                        ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>> =
                            constructArchiveTree(
                                artifact, resolver, trace.child(artifact.metadata.descriptor)
                            )

                        override fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, R : RepositorySettings, M : ArtifactMetadata<D, ArtifactMetadata.ParentInfo<T, R>>> cache(
                            request: T,
                            repository: R,
                            resolver: ArchiveNodeResolver<D, T, *, R, M>
                        ): AsyncJob<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>> =
                            asyncJob cacheAsync@{
                                checkRegistration(resolver)

                                val descriptor = request.descriptor
                                if (isCached(descriptor, resolver)) return@cacheAsync readArchiveTree(
                                    descriptor,
                                    resolver,
                                    ArchiveTrace(descriptor, null)
                                )().merge()

                                cache(resolveArtifact(request, repository, resolver)().merge(), resolver)().merge()
                            }

                        override fun withResource(name: String, resource: Resource) {
                            resources[name] = CacheableArchiveResource(resource)
                        }

                        override fun newData(
                            descriptor: D,
                            parents: List<Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>>>
                        ): Tree<Tagged<ArchiveData<*, *>, ArchiveNodeResolver<*, *, *, *, *>>> =
                            Tree(
                                ArchiveData(
                                    descriptor,
                                    CacheableArchiveResource::class,
                                    resources
                                ) tag resolver,
                                parents
                            )
                    }
                )().merge()
        }
}