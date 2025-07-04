package dev.extframework.boot.archive

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.resources.LocalResource
import com.durganmcbroom.resources.Resource
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.API_VERSION
import dev.extframework.boot.audit.Auditors
import dev.extframework.boot.audit.audit
import dev.extframework.boot.getLogger
import dev.extframework.boot.monad.*
import dev.extframework.boot.util.*
import dev.extframework.common.util.copyTo
import dev.extframework.common.util.filterDuplicates
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.`object`.ObjectContainer
import dev.extframework.`object`.ObjectContainerImpl
import kotlinx.coroutines.awaitAll
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
import kotlin.io.path.Path
import kotlin.io.path.writeBytes
import kotlin.reflect.jvm.jvmName

public open class DefaultArchiveGraph @JvmOverloads constructor(
    path: Path,
    override val nodes: MutableMap<ArtifactMetadata.Descriptor, Tagged<ArchiveNode<*>, ArchiveNodeResolver<*, *, *, *, *>>> = HashMap()
) : ArchiveGraph {
    protected open val logger: Logger = getLogger()

    override val resolvers: ObjectContainer<ArchiveNodeResolver<*, *, *, *, *>> =
        object : ObjectContainerImpl<ArchiveNodeResolver<*, *, *, *, *>>(ConcurrentHashMap()) {
            override fun register(obj: ArchiveNodeResolver<*, *, *, *, *>): Boolean {
                if (obj is RegisterAuditor) {
                    auditors = obj.register(auditors)
                }

                return super.register(obj)
            }
        }

    override val path: Path = path resolve "v$API_VERSION"

    override var auditors: Auditors = Auditors()


    /**
     * Determines if the given descriptor has been previously cached
     *
     * @param descriptor The descriptor.
     * @param resolver The resolver that loaded the descriptor.
     *
     * @return Whether that archive is cached.
     */
    protected open fun <D : ArtifactMetadata.Descriptor> isCached(
        descriptor: D,
        resolver: ArchiveNodeResolver<D, *, *, *, *>
    ): Boolean {
        checkRegistration(resolver)

        if (nodes.contains(descriptor)) {
            return true
        }

        val metadataPath = metadataPath(resolver, descriptor)

        return Files.exists(metadataPath)
    }

    protected fun checkRegistration(resolver: ArchiveNodeResolver<*, *, *, *, *>) {
        if (resolvers.contains(resolver.id)) return
        resolvers.register(resolver)
    }

    private suspend fun <K : ArtifactMetadata.Descriptor, N : ArchiveNode<K>> checkLoaded(
        descriptor: K,
        resolver: ArchiveNodeResolver<K, *, *, *, *>,
        or: suspend () -> N
    ): N = nodes[descriptor]?.let { node ->
        check(resolver.nodeType.isInstance(node.value)) { "Archive node: '$descriptor' was found loaded but it does not match the expected type of: '${resolver.nodeType.name}'. Its type was: '${node::class.java.name}" }

        node.value as N
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
    override suspend fun <K : ArtifactMetadata.Descriptor, T : ArchiveNode<K>> get(
        descriptor: K,
        resolver: ArchiveNodeResolver<K, *, T, *, *>
    ): T {
        checkRegistration(resolver)

        return checkLoaded(descriptor, resolver) {
            if (!isCached(descriptor, resolver)) throw ArchiveException.ArchiveNotCached(
                descriptor.name, ArchiveTrace(descriptor)
            )

            val archiveTree: Tree<TaggedIArchive> =
                readArchiveTree(`3`(
                    descriptor,
                    resolver,
                    ArchiveTrace(descriptor)
                ))

            val auditedTree = auditors[ArchiveTreeAuditContext::class].audit(
                ArchiveTreeAuditContext(
                    archiveTree,
                    ArchiveTrace(descriptor), this@DefaultArchiveGraph
                )
            ).tree

            logger.info(
                "Archive tree of '$descriptor' after auditing:\n" +
                        textifyTree(auditedTree.toGraphable { it.value.descriptor.name })
            )

            getInternal(`2`(
                auditedTree,
                ArchiveTrace(descriptor)
            )) as T
        }
    }

    override suspend fun unload(descriptor: ArtifactMetadata.Descriptor): List<ArchiveNode<*>> {
        val node = nodes[descriptor]?.value ?: return listOf()

        val canAccess = node.access.targets.mapTo(HashSet()) {
            it.descriptor
        } + descriptor

        val uniquelyAccessed = nodes
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
            nodes[it]!!.value
        }

        for (node in result) {
            nodes.remove(node.descriptor)

            getInternal.remove(node.descriptor)
            readArchiveTree.remove(node.descriptor)
            constructArchiveTree.remove(node.descriptor)
            cacheInternal.remove(node.descriptor)
        }

        return result
            .asReversed()
            .filterDuplicates()
            .asReversed()
    }

    /**
     * Recursively reads the given archive tree from the cache. This can throw
     * if not all required resolvers are registered before this is
     * called.
     */

    private val readArchiveTree = MemoizationPool<
            `3`<ArtifactMetadata.Descriptor, ArchiveNodeResolver<*, *, *, *, *>, ArchiveTrace>,
            Tree<TaggedIArchive>
            >({ it.first }) f@{ (descriptor, resolver, trace) ->
        trace.checkCircularity()
        resolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>

        val loadedNode = nodes[descriptor]?.value
        if (loadedNode != null) {
            return@f nodeToTree(loadedNode, trace)
        }

        // Locking just to create the job, while this method is recursive
        // no deadlocks should occur because the lock will release before
        // child jobs start/complete.
        val metadataPath = metadataPath(resolver, descriptor)
        val info = basicObjectMapper.readValue<CacheableArchiveData>(
            Files.newInputStream(metadataPath)
        )

        val parents = info.access.mapAsync {
            val currResolver = resolvers[it.resolver]
                ?: throw IllegalStateException("Resolver: '${it.resolver}' not found when hydrating cache. Please register it.")

            val currDescriptor = currResolver.deserializeDescriptor(it.descriptor, trace)

            call(`3`(
                currDescriptor,
                currResolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>,
                trace.child(currDescriptor)
            ))
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

    /**
     * Given an archive tree performs the necessary operations to load it.
     */
    private val getInternal = MemoizationPool<
            `2`<Tree<TaggedIArchive>, ArchiveTrace>,
            ArchiveNode<*>>({ it.first.item.value.descriptor })
    f@{ (tree: Tree<TaggedIArchive>, trace: ArchiveTrace) ->
        val currDescriptor = tree.item.value.descriptor

        val alreadyLoadedNode = nodes[currDescriptor]?.value
        if (alreadyLoadedNode != null) return@f alreadyLoadedNode

        val (data, resolver) = tree.item

        val parents = tree.parents.mapAsync {
            call(it to trace.child(it.item.value.descriptor))
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
        ).tree

        val node = (resolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>)
            .load(
                (data as ArchiveData<ArtifactMetadata.Descriptor, CachedArchiveResource>),
                auditedTree,
                object : ResolutionHelper {
                    override val trace: ArchiveTrace = trace
                }
            ) as ArchiveNode<ArtifactMetadata.Descriptor>

        nodes[node.descriptor] = node.tag(resolver)

        node
    }

    /**
     * Caches the given request + repository as defined by the supertype.
     */
    override suspend fun <
            D : ArtifactMetadata.Descriptor,
            T : ArtifactRequest<D>,
            R : RepositorySettings,
            M : ArtifactMetadata<D, ArtifactMetadata.ParentInfo<T, R>>> cache(
        request: T,
        repository: R,
        resolver: ArchiveNodeResolver<D, T, *, R, M>
    ): Tree<TaggedIArchive> {
        checkRegistration(resolver)

        val descriptor = request.descriptor
        if (isCached(descriptor, resolver)) return readArchiveTree(`3`(
            descriptor,
            resolver,
            ArchiveTrace(descriptor, null)
        ))

        val artifactTree: Tree<Either<M, TaggedIArchive>> = resolveArtifact(request, repository, resolver)

        return cacheInternal(artifactTree, resolver)
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
    private suspend fun <D : ArtifactMetadata.Descriptor, M : ArtifactMetadata<D, *>> cacheInternal(
        artifactTree: Tree<Either<M, TaggedIArchive>>,
        resolver: ArchiveNodeResolver<D, *, *, *, M>
    ): Tree<TaggedIArchive> {
        val trace = ArchiveTrace(
            artifactTree.item.map({ it.descriptor }, { it.value.descriptor }),
        )

        val archiveTree = constructArchiveTree.submit(
            `3`(artifactTree, resolver, trace)
        ).toTree()

        return cacheInternal.submit(
            archiveTree to trace
        )
    }

    private val cacheInternal = MemoizationPool<
            `2`<Tree<TaggedIArchive>, ArchiveTrace>,
            Tree<TaggedIArchive>
            >({ it.first.item.value.descriptor }) f@{ (tree, trace) ->

        trace.checkCircularity()

        val data = tree.item.value as? ArchiveData<*, *> ?: return@f nodeToTree(
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
            return@f readArchiveTree(`3`(data.descriptor, resolver, trace))
        }

        data as ArchiveData<*, CacheableArchiveResource>

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
                    it.tag.id,
                    parentDescriptor
                )
            }
        ).let(ObjectMapper().registerModule(KotlinModule.Builder().build())::writeValueAsBytes)

        val parents = tree.parents.mapAsync {
            call(it to trace.child(it.item.value.descriptor))
        }.onEach { it.start() }

        resourcePaths.mapAsync { (name, wrapper, path) ->
            if (wrapper.resource !is LocalResource) {
                logger.info("Retrieving: '${data.descriptor}#$name' from: '${wrapper.resource.location}'")
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
            ) tag resolver, parents.awaitAll())
    }

    /**
     * Resolves the requests artifact and maps NotFound exceptions
     * to [ArchiveException.ArchiveNotFound]
     */
    private suspend fun <
            D : ArtifactMetadata.Descriptor,
            T : ArtifactRequest<D>,
            R : RepositorySettings,
            M : ArtifactMetadata<D, ArtifactMetadata.ParentInfo<T, R>>> resolveArtifact(
        request: T,
        repository: R,
        resolver: ArchiveNodeResolver<D, T, *, R, M>,
    ): Tree<Either<M, TaggedIArchive>> {
        checkRegistration(resolver)

        try {
            return resolveArtifact<D, T, R, M>()(
                `4`(
                    request,
                    listOf(repository),
                    resolver,
                    ArchiveTrace(request.descriptor)
                )
            )
        } catch (e: ArchiveException.ArchiveNotFound) {
            throw e
        } catch (e: Exception) {
            throw ArchiveException(
                ArchiveTrace(request.descriptor, null),
                null,
                e
            )
        }
    }

    private fun <D : ArtifactMetadata.Descriptor,
            T : ArtifactRequest<D>,
            R : RepositorySettings,
            M : ArtifactMetadata<D, ArtifactMetadata.ParentInfo<T, R>>> resolveArtifact(
    ) = MemoizedRecursiveAsynchronousFunction<
            `4`<T, List<R>, ArchiveNodeResolver<D, T, *, R, M>, ArchiveTrace>,
            Tree<Either<M, TaggedIArchive>>
            >({ it.first.descriptor }) f@{ (
                                               request: T,
                                               candidates: List<R>,
                                               resolver: ArchiveNodeResolver<D, T, *, R, M>,
                                               trace: ArchiveTrace
                                           ) ->
        if (trace.isCircular())
            throw ArtifactResolutionException.CircularArtifacts(trace.toList())

        if (isCached(request.descriptor, resolver)) {
            return@f readArchiveTree(`3`(request.descriptor, resolver, trace)).map {
                Either.That(it)
            }
        }

        val exceptions = concurrentList<Throwable>()

        val metadata = candidates.firstNotNullOfOrNull { candidate ->
            runCatching {
                resolver.factory.createNew(candidate).get(request)
            }.onFailure {
                exceptions.add(it)
            }.getOrNull() ?: return@firstNotNullOfOrNull null
        }

        if (metadata != null) {
            val newChildren = metadata.parents
                .mapAsync { child ->
                    call(
                        `4`(
                            child.request,
                            child.candidates,
                            resolver,
                            trace.child(child.request.descriptor)
                        )
                    )
                }.awaitAll()

            return@f Tree(
                Either.This(metadata),
                newChildren
            )
        }

        val exceptional = exceptions
            .firstOrNull { it !is MetadataRequestException.MetadataNotFound }

        if (exceptional != null) throw exceptional

        throw ArchiveException.ArchiveNotFound(
            trace,
            request.descriptor,
            candidates,
        )
    }

    private val constructArchiveTree =
        MemoizationPool<
                `3`<Tree<Either<ArtifactMetadata<*, *>, TaggedIArchive>>, ArchiveNodeResolver<*, *, *, *, *>, ArchiveTrace>,
                Tree<TaggedIArchive>
                >({ p ->
            p.first.item.map(
                { it.descriptor },
                { it.value.descriptor })
        }) f@{ (artifact, resolver, trace) ->
            checkRegistration(resolver)
            trace.checkCircularity()

            if (artifact.item.isThat) return@f artifact.map {
                it.getThat()
            }

            val metadata = artifact.item.getThis()

            if (!resolver.metadataType.isInstance(metadata)) throw ArchiveException(
                trace,
                "Invalid metadata type for artifact: '$artifact', expected the entire tree to be of type: '${resolver.metadataType::class.jvmName}'",
            )

            val resolver =
                resolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, ArtifactMetadata<*, *>>

            if (isCached(metadata.descriptor, resolver)) {
                return@f readArchiveTree(`3`(metadata.descriptor, resolver, trace))
            }

            resolver
                .cache(
                    metadata,
                    artifact.parents,
                    object : CacheHelper<ArtifactMetadata.Descriptor> {
                        private var resources: MutableMap<String, CacheableArchiveResource> = HashMap()

                        override val trace: ArchiveTrace = trace

                        override suspend fun <D : ArtifactMetadata.Descriptor, M : ArtifactMetadata<D, *>> cache(
                            artifact: Tree<Either<M, TaggedIArchive>>,
                            resolver: ArchiveNodeResolver<D, *, *, *, M>
                        ): Tree<TaggedIArchive> =
                            call(
                                `3`(
                                    artifact,
                                    resolver,
                                    trace.child(artifact.item.map({ it.descriptor }, { it.value.descriptor }))
                                )
                            )

                        override suspend fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, R : RepositorySettings> cache(
                            request: T,
                            repository: R,
                            resolver: ArchiveNodeResolver<D, T, *, R, *>
                        ): Tree<TaggedIArchive> {
                            checkRegistration(resolver)

                            val descriptor = request.descriptor
                            if (isCached(descriptor, resolver)) return readArchiveTree(`3`(
                                descriptor,
                                resolver,
                                ArchiveTrace(descriptor, null)
                            ))

                            return cache(
                                resolveArtifact(
                                    request,
                                    repository,
                                    resolver
                                ) as Tree<Either<ArtifactMetadata<D, *>, TaggedIArchive>>,
                                resolver as ArchiveNodeResolver<D, *, *, *, ArtifactMetadata<D, *>>
                            )
                        }

                        override fun withResource(name: String, resource: Resource) {
                            resources[name] = CacheableArchiveResource(resource)
                        }

                        override suspend fun newData(
                            descriptor: ArtifactMetadata.Descriptor,
                            parents: List<Tree<TaggedIArchive>>
                        ): Tree<TaggedIArchive> =
                            Tree(
                                ArchiveData(
                                    descriptor,
                                    CacheableArchiveResource::class,
                                    resources
                                ) tag resolver,
                                parents
                            )
                    }
                )
        }

    protected open fun <T : ArtifactMetadata.Descriptor> metadataPath(
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
    ): Tree<TaggedIArchive> {
        val value = nodes[node.descriptor] ?: throw ArchiveException(
            trace,
            "Archive node: '${node.descriptor}' was not loaded?"
        )

        return Tree(
            value,
            node.access.targets
                .filter { target -> nodes.contains(target.descriptor) }
                .map { nodeToTree(it.relationship.node, trace.child(it.descriptor)) }
        )
    }
}