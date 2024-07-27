package dev.extframework.boot.archive

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.*
import com.durganmcbroom.jobs.logging.LogLevel
import com.durganmcbroom.jobs.logging.logger
import com.durganmcbroom.resources.LocalResource
import com.durganmcbroom.resources.Resource
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.API_VERSION
import dev.extframework.boot.archive.audit.AuditContext
import dev.extframework.boot.monad.*
import dev.extframework.common.util.copyTo
import dev.extframework.common.util.make
import dev.extframework.common.util.resolve
import dev.extframework.`object`.MutableObjectContainer
import dev.extframework.`object`.ObjectContainerImpl
import java.nio.file.Files
import java.nio.file.Path
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
    private inner class DefaultAuditContext(override val trace: ArchiveTrace) : AuditContext {
        override fun isLoaded(descriptor: ArtifactMetadata.Descriptor): Boolean {
            return loaded(descriptor)
        }
    }

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
    override fun <K : ArtifactMetadata.Descriptor, T : ArchiveNode<K>> get(
        descriptor: K,
        resolver: ArchiveNodeResolver<K, *, T, *, *>
    ): Job<T> = job {
        getNode(descriptor)?.let { node ->
            check(resolver.nodeType.isInstance(node)) { "Archive node: '$descriptor' was found loaded but it does not match the expected type of: '${resolver.nodeType.name}'. Its type was: '${node::class.java.name}" }

            node as T
        } ?: withContext(ArchiveTraceFactory) {
            check(
                isCached(
                    descriptor,
                    resolver
                )
            ) { "Archive node: '$descriptor' is not cached. It must be cached before use." }

            val archiveTree = readArchiveTree(
                descriptor,
                resolver,
                ArchiveTrace(descriptor)
            )().merge()

            val auditedTree = resolver.auditors.archiveTreeAuditor.audit(
                archiveTree,
                DefaultAuditContext(
                    ArchiveTrace(descriptor)
                )
            )().merge()

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
    ): Job<Tree<Tagged<ArchiveData<*, CachedArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>>> =
        job {
            trace.checkCircularity()

            val metadataPath = path resolve resolver.pathForDescriptor(descriptor, "archive-metadata", "json")
            val info = jacksonObjectMapper().readValue<CacheableArchiveData>(
                metadataPath.toFile()
            )

            val parents = info.access.map {
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
                    resources
                ).tag(resolver),
                parents
            )
        }

    /**
     * Given an archive tree performs the necessary operations to load it.
     */
    @Suppress("UNCHECKED_CAST")
    private fun getInternal(
        tree: Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>,
        trace: ArchiveTrace
    ): Job<ArchiveNode<*>> = job {
        val (data, resolver) = tree.item

        val parents = tree.parents.map {
            getInternal(it, trace.child(it.item.value.descriptor))().merge()
        }

        val accessTree = object : ArchiveAccessTree {
            override val descriptor: ArtifactMetadata.Descriptor = tree.item.value.descriptor
            override val targets: List<ArchiveTarget> = (parents
                .asSequence()
                .filterIsInstance<ClassLoadedArchiveNode<*>>()
                .map {
                    ArchiveTarget(
                        it.descriptor,
                        ArchiveRelationship.Direct(
                            it
                        )
                    )
                } +
                    parents
                        .asSequence()
                        .filterIsInstance<ClassLoadedArchiveNode<*>>()
                        .flatMap { it.access.targets }
                        .map {
                            ArchiveTarget(
                                it.descriptor,
                                ArchiveRelationship.Transitive(
                                    it.relationship.node
                                )
                            )
                        }).toList()
        }

        val auditedTree = tree.item.tag.auditors.accessAuditor.audit(
            accessTree,
            DefaultAuditContext(trace)
        )().merge()

        (resolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>)
            .load(
                (data as ArchiveData<ArtifactMetadata.Descriptor, CachedArchiveResource>),
                auditedTree,
                object : ResolutionHelper {
                    override val trace: ArchiveTrace = trace
                }
            )().merge()
    }

    /**
     * Caches the given request + repository as defined by the supertype.
     */
    override fun <
            D : ArtifactMetadata.Descriptor,
            T : ArtifactRequest<D>,
            R : RepositorySettings,
            M : ArtifactMetadata<D, *>> cache(
        request: T,
        repository: R,
        resolver: ArchiveNodeResolver<D, T, *, R, M>
    ): Job<Unit> = job {
        val descriptor = request.descriptor
        if (isCached(descriptor, resolver)) return@job

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
    ): Job<Unit> = job {
        val archiveTree =
            constructArchiveTree(artifactTree, resolver, ArchiveTrace(artifactTree.metadata.descriptor))().merge()
                .toTree()

        cacheInternal(archiveTree, ArchiveTrace(artifactTree.metadata.descriptor, null))().merge()
    }

    /**
     * Recursively caches the given tree of archive data.
     */
    private fun cacheInternal(
        tree: Tree<Tagged<ArchiveData<*, CacheableArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>>,
        trace: ArchiveTrace
    ): Job<Unit> = job {
        trace.checkCircularity()
        val result = tree.item.value
        val resolver = tree.item.tag as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>

        val metadataPath = path resolve resolver.pathForDescriptor(result.descriptor, "archive-metadata", "json")

        val resourcePaths = result.resources.map { (name, wrapper) ->
            val (classifier, extension) = name
                .split(".")
                .takeIf { it.size == 2 }
                ?: throw ArchiveException(
                    trace,
                    "Resource name should be in the format : '<CLASSIFIER>.<TYPE>'. Found: '$name'",
                )

            val path =
                this@DefaultArchiveGraph.path resolve if (wrapper.resource is LocalResource) Path.of(wrapper.resource.location)
                else resolver.pathForDescriptor(result.descriptor, classifier, extension)

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
                    resolver.name,
                    parentDescriptor
                )
            }
        ).let(ObjectMapper().registerModule(KotlinModule.Builder().build())::writeValueAsBytes)

        resourcePaths.forEach { (_, wrapper, path) ->
            if (wrapper.resource !is LocalResource) (wrapper.resource copyTo path)
        }

        metadataPath
            .apply { make() }
            .writeBytes(metadataResource)

        tree.parents
            .forEach {
                cacheInternal(it, trace)().merge()
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
            M : ArtifactMetadata<D, *>> resolveArtifact(
        request: T,
        repository: R,
        resolver: ArchiveNodeResolver<D, T, *, R, M>,
    ): Job<Artifact<M>> {
        val descriptor = request.descriptor

        return job(JobName("Resolve artifact: '${descriptor.name}'")) {
            val context = resolver.createContext(repository)

            logger.log(LogLevel.INFO, "Building artifact tree for: '$descriptor'...")

            val artifact = context.getAndResolve(request)().mapException {
                if (it is ArtifactException.ArtifactNotFound) ArchiveException.ArchiveNotFound(
                    ArchiveTrace(request.descriptor, null),
                    it.desc,
                    it.searchedIn
                ) else ArchiveException(ArchiveTrace(request.descriptor, null), null, it)
            }.merge()

            artifact
        }
    }

    /**
     * Builds an [ArchiveData] tree from an [Artifact].
     */
    protected fun <
            D : ArtifactMetadata.Descriptor,
            M : ArtifactMetadata<D, *>,
            > constructArchiveTree(
        artifact: Artifact<M>,
        resolver: ArchiveNodeResolver<D, *, *, *, M>,
        trace: ArchiveTrace,
    ): Job<Tree<Tagged<ArchiveData<*, CacheableArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>>> =
        job(
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
                        ): Job<Tree<Tagged<ArchiveData<*, CacheableArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>>> =
                            constructArchiveTree(
                                artifact, resolver, trace.child(artifact.metadata.descriptor)
                            )

                        override fun <D : ArtifactMetadata.Descriptor, T : ArtifactRequest<D>, R : RepositorySettings, M : ArtifactMetadata<D, *>> resolveArtifact(
                            request: T,
                            repository: R,
                            resolver: ArchiveNodeResolver<D, T, *, R, M>
                        ): Job<Artifact<M>> {
                            return this@DefaultArchiveGraph.resolveArtifact(request, repository, resolver)
                        }

                        override fun withResource(name: String, resource: Resource) {
                            resources[name] = CacheableArchiveResource(resource)
                        }

                        override fun newData(
                            descriptor: D,
                            parents: List<Tree<Tagged<ArchiveData<*, CacheableArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>>>
                        ): Tree<Tagged<ArchiveData<*, CacheableArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>> =
                            Tree(
                                ArchiveData(
                                    descriptor,
                                    resources
                                ) tag resolver,
                                parents
                            )
                    }
                )().merge()
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

