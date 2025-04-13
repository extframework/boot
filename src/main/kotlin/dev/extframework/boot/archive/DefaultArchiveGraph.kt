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
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.API_VERSION
import dev.extframework.boot.audit.Auditors
import dev.extframework.boot.audit.audit
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.monad.tag
import dev.extframework.boot.monad.toTree
import dev.extframework.boot.util.basicObjectMapper
import dev.extframework.boot.util.textifyTree
import dev.extframework.boot.util.toGraphable
import dev.extframework.boot.util.withSuspendingLock
import dev.extframework.common.util.copyTo
import dev.extframework.common.util.filterDuplicates
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.`object`.MutableObjectContainer
import dev.extframework.`object`.ObjectContainer
import dev.extframework.`object`.ObjectContainerImpl
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.writeBytes
import kotlin.reflect.jvm.jvmName

public open class DefaultArchiveGraph @JvmOverloads constructor(
    path: Path,
    protected open val mutable: MutableMap<ArtifactMetadata.Descriptor, Tagged<ArchiveNode<*>, ArchiveNodeResolver<*, *, *, *, *>>> = HashMap()
) : ArchiveGraph {
    protected open val resolvers: MutableObjectContainer<ArchiveNodeResolver<*, *, *, *, *>> = ObjectContainerImpl()

    public val theResolvers: ObjectContainer<ArchiveNodeResolver<*, *, *, *, *>>
        get() = resolvers
    public val theGraph: Map<ArtifactMetadata.Descriptor, Tagged<ArchiveNode<*>, ArchiveNodeResolver<*, *, *, *, *>>>
        get() = mutable

    override val path: Path = path resolve "v$API_VERSION"

    override var auditors: Auditors = Auditors()

    /**
     * Register a resolver.
     *
     * @param resolver the resolver.
     */
    override fun registerResolver(resolver: ArchiveNodeResolver<*, *, *, *, *>): Unit = synchronized(this) {
        if (resolver is RegisterAuditor) {
            auditors = resolver.register(auditors)
        }

        resolvers.register(resolver.name, resolver)

        Unit
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
    override fun getNode(descriptor: ArtifactMetadata.Descriptor): ArchiveNode<*>? = mutable[descriptor]?.value

    /**
     * Returns a collection of all the nodes in this archive graph.
     *
     * @return All the currently loaded nodes.
     */
    override fun nodes(): Collection<ArchiveNode<*>> = mutable.values.map { it.value }

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

        val metadataPath = metadataPath(resolver, descriptor)

        return Files.exists(metadataPath)
    }

    private fun checkRegistration(resolver: ArchiveNodeResolver<*, *, *, *, *>) {
        if (resolvers.has(resolver.name)) return
        registerResolver(resolver)
    }

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

            val archiveTree: Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>> =
                readArchiveTree(
                    descriptor,
                    resolver,
                    ArchiveTrace(descriptor)
                )().merge()

            val auditedTree = auditors[ArchiveTreeAuditContext::class].audit(
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

    override fun unload(descriptor: ArtifactMetadata.Descriptor): AsyncJob<List<ArchiveNode<*>>> = asyncJob {
        val node = getNode(descriptor) ?: return@asyncJob listOf()

        val canAccess = node.access.targets.mapTo(HashSet()) {
            it.descriptor
        } + descriptor

        val uniquelyAccessed = mutable
            .filterNot { canAccess.contains(it.key) }
            .flatMapTo(HashSet()) { (_, it) -> it.value.access.targets.map { it.descriptor } }

        if (uniquelyAccessed.contains(descriptor)) {
            throw ArchiveException.UnloadingConstrained(
                ArchiveTrace(descriptor, null),
                // TODO
                setOf()
            )
        }

       val result = canAccess.filterNot {
            uniquelyAccessed.contains(it)
        }.map {
            getNode(it)!!
        }

//        val backReferences = HashMap<ArtifactMetadata.Descriptor, MutableList<ArtifactMetadata.Descriptor>>()
//
//        for (node in nodes()) {
//            for (target in node.access.targets.filter { it.relationship is ArchiveRelationship.Direct }) {
//                backReferences.getOrPut(target.descriptor) {
//                    ArrayList()
//                }.add(node.descriptor)
//            }
//        }
//

//
//        fun fullPath(
//            descriptor: ArtifactMetadata.Descriptor,
//        ): List<List<ArtifactMetadata.Descriptor>> {
//            return (backReferences[descriptor] ?: listOf()).flatMap { b ->
//                fullPath(b).map {
//                    listOf(descriptor) + it
//                }
//            }.takeIf { it.isNotEmpty() } ?: listOf(listOf(descriptor))
//        }
//
//        val fullBackReferences = backReferences.mapValues { (key) -> fullPath(key) }
//
//        val result = ArrayList<ArchiveNode<*>>()
//
//        val edge = getNode(descriptor)?.let { mutableListOf(it) } ?: return@asyncJob listOf()
//        result.addAll(edge)
//
//        while (edge.isNotEmpty()) {
//            val current = edge.removeAt(0)
//
//            if ((fullBackReferences[current.descriptor]?.size ?: 0) <= 1) {
//                result.add(current)
//            }
//
//            for (target in current.access.targets.filter { it.relationship is ArchiveRelationship.Direct }) {
//                edge.add(target.relationship.node)
//            }
////            for (target in current.access.targets.filter { it.relationship is ArchiveRelationship.Direct }) {
////                val references = backReferences[target.descriptor]
////                if (references?.contains(current.descriptor) == true || references?.size == 1) {
////                    result.add(target.relationship.node)
////                    edge.add(target.relationship.node)
////                }
////            }
//        }
//
        for (node in result) {
            mutable.remove(node.descriptor)
        }
        result
            .asReversed()
            .filterDuplicates()
            .asReversed()
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
    protected val beingRead: MutableMap<
            ArtifactMetadata.Descriptor,
            Deferred<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>
            > = ConcurrentHashMap()
    protected val readingMutex: Mutex = Mutex()

    protected fun <T : ArtifactMetadata.Descriptor> readArchiveTree(
        descriptor: T,
        resolver: ArchiveNodeResolver<T, *, *, *, *>,
        trace: ArchiveTrace
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        coroutineScope {
            trace.checkCircularity()

            val loadedNode = getNode(descriptor)
            if (loadedNode != null) {
                return@coroutineScope nodeToTree(loadedNode, trace)
            }

            // Locking just to create the job, while this method is recursive
            // no deadlocks should occur because the lock will release before
            // child jobs start/complete.
            val job = readingMutex.withSuspendingLock {
                val job = beingRead[descriptor] ?: async {
                    val metadataPath = metadataPath(resolver, descriptor)
                    val info = basicObjectMapper.readValue<CacheableArchiveData>(
                        Files.newInputStream(metadataPath)
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
                        CachedArchiveResource(Path(value))
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

                beingRead[descriptor] = job

                job
            }

            job.await()
        }
    }

    // Represents all the currently running get jobs
    private val beingGotten: MutableMap<
            ArtifactMetadata.Descriptor,
            Deferred<ArchiveNode<*>>
            > = ConcurrentHashMap()
    private val gettingMutex = Mutex()

    /**
     * Given an archive tree performs the necessary operations to load it.
     *
     * Its extremely important that this is safe concurrently because of how if two
     * of the same archives are loaded, similar libraries could have linkage errors
     * or just incompatibilities when trying to pass same but separately loaded classes
     * into one each other.
     */
    @Suppress("UNCHECKED_CAST")
    private fun getInternal(
        tree: Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>,
        trace: ArchiveTrace
    ): AsyncJob<ArchiveNode<*>> = asyncJob {
        coroutineScope {
            val currDescriptor = tree.item.value.descriptor

            val alreadyLoadedNode = getNode(currDescriptor)
            if (alreadyLoadedNode != null) return@coroutineScope alreadyLoadedNode

            // Lock to ensure there really only is one being gotten at a time
            val job = gettingMutex.withSuspendingLock {
                // Either get a currently running job or start a new one
                val job = beingGotten[currDescriptor] ?: async {
                    val (data, resolver) = tree.item

                    val parents = tree.parents.mapAsync {
                        getInternal(it, trace.child(it.item.value.descriptor))().merge()
                    }.awaitAll()

                    val accessTree = object : ArchiveAccessTree {
                        override val descriptor: ArtifactMetadata.Descriptor = currDescriptor
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
                            .filterDuplicates()
                    }

                    val auditedTree = auditors[ArchiveAccessAuditContext::class].audit(
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

                    mutable[node.descriptor] = node.tag(resolver)

                    beingGotten.remove(currDescriptor)

                    node
                }

                // Immediately add to cache
                beingGotten[currDescriptor] = job

                // Returning the already active or newly created job, it's
                // safe to lock here because this should take <1ms
                job
            }

            job.await()
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
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
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
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        val archiveTree =
            constructArchiveTree(artifactTree, resolver, ArchiveTrace(artifactTree.metadata.descriptor))().merge()
                .toTree()

        cacheInternal(archiveTree, ArchiveTrace(artifactTree.metadata.descriptor, null))().merge()
    }

    // Represents all the currently running cache jobs
    private val beingCached: MutableMap<
            ArtifactMetadata.Descriptor,
            Deferred<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>
            > = ConcurrentHashMap()
    private val cacheMutex = Mutex()

    /**
     * Recursively caches the given tree of archive data.
     */
    private fun cacheInternal(
        tree: Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>,
        trace: ArchiveTrace
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = asyncJob {
        coroutineScope {
            trace.checkCircularity()

            val data = tree.item.value as? ArchiveData<*, *> ?: return@coroutineScope nodeToTree(
                tree.item.value as? ArchiveNode<*> ?: throw ArchiveException(
                    trace,
                    "Unknown IArchive type. Only acceptable types are ArchiveData, or implement from ArchiveNode.",
                    null
                ), trace
            )
            val resolver = tree.item.tag as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>

            if (isCached(
                    data.descriptor,
                    resolver
                ) || data.resourceType != CacheableArchiveResource::class
            ) {
                return@coroutineScope readArchiveTree(data.descriptor, resolver, trace)().merge()
            }

            data as ArchiveData<*, CacheableArchiveResource>

            val job = cacheMutex.withSuspendingLock {
                beingCached[data.descriptor] ?: run {
                    val job = async {
                        val metadataPath = metadataPath(resolver, data.descriptor)

                        val resourcePaths = data.resources.map { (name, wrapper) ->
                            val (classifier, extension) = name
                                .split(".")
                                .takeIf { it.size == 2 }
                                ?: throw ArchiveException(
                                    trace,
                                    "Resource name should be in the format : '<CLASSIFIER>.<TYPE>'. Found: '$name'",
                                )

                            val path =
                                this@DefaultArchiveGraph.path resolve if (wrapper.resource is LocalResource) Path(
                                    wrapper.resource.location
                                )
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
                            cacheInternal(it, trace.child(it.item.value.descriptor))().merge()
                        }.onEach { it.start() }

                        resourcePaths.mapAsync { (name, wrapper, path) ->
                            if (wrapper.resource !is LocalResource) {
                                info("Retrieving: '${data.descriptor}#$name' from: '${wrapper.resource.location}'")
                                wrapper.resource copyTo path
                            }
                        }.awaitAll()

                        metadataPath
                            .apply { make() }
                            .writeBytes(metadataResource)

                        beingCached.remove(data.descriptor)

                        Tree(
                            ArchiveData(
                                tree.item.value.descriptor,
                                CachedArchiveResource::class,
                                resourcePaths.associate {
                                    it.first to CachedArchiveResource(it.third)
                                }
                            ) tag resolver, parents.awaitAll())
                    }

                    beingCached[data.descriptor] = job

                    job
                }
            }

            job.await()
        }
    }

    // Represents all the currently running resolution jobs
    private val beingResolved: MutableMap<
            Triple<ArtifactRequest<*>, ArchiveNodeResolver<*, *, *, *, *>, RepositorySettings>,
            Deferred<Artifact<*>>
            > = ConcurrentHashMap()
    private val resolutionMutex = Mutex()

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
            coroutineScope {
                val job = resolutionMutex.withSuspendingLock {
                    val key = Triple(request, resolver, repository)
                    beingResolved[key] ?: run {
                        val job = async {
                            val context = resolver.context

                            context.getAndResolveAsync(request, repository)().mapException {
                                if (it is ArtifactException.ArtifactNotFound) ArchiveException.ArchiveNotFound(
                                    ArchiveTrace(request.descriptor, null),
                                    it.desc,
                                    it.searchedIn,
                                    it
                                ) else ArchiveException(ArchiveTrace(request.descriptor, null), null, it)
                            }.merge()
                        }

                        beingResolved[key] = job

                        job
                    }
                }

                job.await() as Artifact<M>
            }
        }
    }

    // Represents all the currently running cache jobs
    private val beingConstructed: MutableMap<
            Pair<ArtifactMetadata.Descriptor, ArchiveNodeResolver<*, *, *, *, *>>,
            Deferred<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>
            > = ConcurrentHashMap()
    private val constructionMutex = Mutex()

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
    ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> =
        asyncJob(
            JobName("Construct archive tree: '${artifact.metadata.descriptor}'")
        ) {
            coroutineScope {
                checkRegistration(resolver)

                if (context[ArchiveTrace]?.isCircular() == true) throw ArchiveException.CircularArtifactException(trace)

                if (isCached(artifact.metadata.descriptor, resolver)) {
                    return@coroutineScope readArchiveTree(artifact.metadata.descriptor, resolver, trace)().merge()
                }

                if (!resolver.metadataType.isInstance(artifact.metadata)) throw ArchiveException(
                    trace,
                    "Invalid metadata type for artifact: '$artifact', expected the entire tree to be of type: '${resolver.metadataType::class.jvmName}'",
                )

                val job = constructionMutex.withSuspendingLock {
                    beingConstructed[artifact.metadata.descriptor to resolver] ?: run {
                        val job = async {
                            (resolver as ArchiveNodeResolver<D, *, *, *, ArtifactMetadata<D, *>>)
                                .cache(
                                    artifact as Artifact<ArtifactMetadata<D, *>>,
                                    object : CacheHelper<D> {
                                        private var resources: MutableMap<String, CacheableArchiveResource> = HashMap()

                                        override val trace: ArchiveTrace = trace

                                        override fun <D : ArtifactMetadata.Descriptor, M : ArtifactMetadata<D, *>> cache(
                                            artifact: Artifact<M>,
                                            resolver: ArchiveNodeResolver<D, *, *, *, M>
                                        ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> =
                                            constructArchiveTree(
                                                artifact, resolver, trace.child(artifact.metadata.descriptor)
                                            )

                                        override fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, R : RepositorySettings> cache(
                                            request: T,
                                            repository: R,
                                            resolver: ArchiveNodeResolver<D, T, *, R, *>
                                        ): AsyncJob<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> =
                                            asyncJob cacheAsync@{
                                                checkRegistration(resolver)

                                                val descriptor = request.descriptor
                                                if (isCached(descriptor, resolver)) return@cacheAsync readArchiveTree(
                                                    descriptor,
                                                    resolver,
                                                    ArchiveTrace(descriptor, null)
                                                )().merge()

                                                cache(
                                                    resolveArtifact(
                                                        request,
                                                        repository,
                                                        resolver
                                                    )().merge() as Artifact<ArtifactMetadata<D, *>>,
                                                    resolver as ArchiveNodeResolver<D, *, *, *, ArtifactMetadata<D, *>>
                                                )().merge()
                                            }

                                        override fun withResource(name: String, resource: Resource) {
                                            resources[name] = CacheableArchiveResource(resource)
                                        }

                                        override fun newData(
                                            descriptor: D,
                                            parents: List<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>
                                        ): Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>> =
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

                        beingConstructed[artifact.metadata.descriptor to resolver] = job

                        job
                    }
                }

                job.await()
            }
        }

    private fun <T : ArtifactMetadata.Descriptor> metadataPath(
        resolver: ArchiveNodeResolver<T, *, *, *, *>,
        descriptor: T
    ): Path {
        val metadataPath =
            path resolve resolver.pathForDescriptor(descriptor, "archive-metadata-v${resolver.apiVersion}", "json")

        return metadataPath
    }

    private fun nodeToTree(
        node: ArchiveNode<*>,
        trace: ArchiveTrace
    ): Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>> {
        val value = mutable[node.descriptor] ?: throw ArchiveException(
            trace,
            "Archive node: '${node.descriptor}' was not loaded?"
        )

        return Tree(
            value,
            node.access.targets
                .filter { target -> mutable.contains(target.descriptor) }
                .map { nodeToTree(it.relationship.node, trace.child(it.descriptor)) }
        )
    }
}