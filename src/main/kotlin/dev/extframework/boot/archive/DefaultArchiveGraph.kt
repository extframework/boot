package dev.extframework.boot.archive

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.*
import com.durganmcbroom.jobs.logging.LogLevel
import com.durganmcbroom.jobs.logging.logger
import com.durganmcbroom.jobs.logging.warning
import com.durganmcbroom.resources.LocalResource
import com.durganmcbroom.resources.Resource
import com.durganmcbroom.resources.toResource
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.extframework.boot.API_VERSION
import dev.extframework.boot.loader.ArchiveClassProvider
import dev.extframework.boot.loader.ArchiveResourceProvider
import dev.extframework.boot.monad.*
import dev.extframework.common.util.LazyMap
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
    private val mutable: MutableMap<ArtifactMetadata.Descriptor, ArchiveNode<*>> = HashMap()
) : ArchiveGraph {
    private val resolvers: MutableObjectContainer<ArchiveNodeResolver<*, *, *, *, *>> = ObjectContainerImpl()

    // Public API

    override val path: Path = path resolve API_VERSION

    override fun registerResolver(resolver: ArchiveNodeResolver<*, *, *, *, *>) {
        resolvers.register(resolver.name, resolver)
    }

    override fun getResolver(name: String): ArchiveNodeResolver<*, *, *, *, *>? {
        return resolvers.get(name)
    }

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

            val archiveTree = readArchiveTree(descriptor, resolver, ArchiveTrace(descriptor, null))().merge()

            getInternal(
                archiveTree
            )().merge() as T
        }
    }

    override fun getNode(descriptor: ArtifactMetadata.Descriptor): ArchiveNode<*>? = mutable[descriptor]

    override fun nodes(): Collection<ArchiveNode<*>> = mutable.values

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

    // Internal API

    private fun <T : ArtifactMetadata.Descriptor> readArchiveTree(
        descriptor: T,
        resolver: ArchiveNodeResolver<T, *, *, *, *>,
        trace: ArchiveTrace
    ): Job<Tree<Tagged<ArchiveData<out ArtifactMetadata.Descriptor, CachedArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>>> =
        job {
            val metadataPath = path resolve resolver.pathForDescriptor(descriptor, "archive-metadata", "json")
            val info = jacksonObjectMapper().readValue<CacheableArchiveInfo>(
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

    private data class CacheableArchiveInfo(
        // Name of resource to the path
        val resources: Map<String, String>,
        val access: List<CacheableParentInfo>,
    )

    private data class CacheableParentInfo(
        val resolver: String,
        val descriptor: Map<String, String>,
    )

    private fun <D : ArtifactMetadata.Descriptor, M : ArtifactMetadata<D, *>> cacheInternal(
        artifactTree: Artifact<M>,
        resolver: ArchiveNodeResolver<D, *, *, *, M>
    ): Job<Unit> = job {
        val archiveTree = constructArchiveTree(artifactTree, resolver)().merge().toTree()

        cacheInternal(archiveTree, ArchiveTrace(artifactTree.metadata.descriptor, null))().merge()
    }

    private fun cacheInternal(
        tree: Tree<Tagged<ArchiveData<*, CacheableArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>>,
        trace: ArchiveTrace
    ): Job<Unit> = job {
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

        val metadataResource = CacheableArchiveInfo(
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

    private fun checkRegistration(resolver: ArchiveNodeResolver<*, *, *, *, *>) {
        if (resolvers.has(resolver.name)) return
        registerResolver(resolver)
    }

    @Suppress("UNCHECKED_CAST")
    private fun getInternal(
        tree: Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>
    ): Job<ArchiveNode<*>> = job {
        val constrainedTree = doConstraints(
            tree,
            listOf(
                (tree.item.tag.negotiator as ConstraintNegotiator<ArtifactMetadata.Descriptor, *>)
                    .constrain(
                        tree.item.value as IArchive<ArtifactMetadata.Descriptor>,
                        ConstraintType.Bound
                    )().merge()
            ) // FIXME not correct, should be configurable at the very least
        )().merge()

        val (data, resolver) = constrainedTree.item

        (resolver as ArchiveNodeResolver<ArtifactMetadata.Descriptor, *, *, *, *>).load(
            (data as ArchiveData<ArtifactMetadata.Descriptor, CachedArchiveResource>) andMany constrainedTree.parents,
            object : ResolutionHelper {
                override fun <T : ArtifactMetadata.Descriptor, N : ArchiveNode<T>> load(
                    iArchive: AndMany<IArchive<T>, Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>,
                    resolver: ArchiveNodeResolver<T, *, N, *, *>
                ): Job<N> {
                    val (item) = iArchive

                    if (item is ArchiveNode<*>) {
                        check(resolver.nodeType.isInstance(item)) { "Attempted to load archive: '$iArchive' with resolver: '$resolver' but it does not match expected type: '${resolver.nodeType}'" }

                        return SuccessfulJob { item as N }
                    }

                    return getInternal(iArchive
                        .mapItem { it.tag(resolver) }
                        .toTree()
                    ) as Job<N>
                }

                override fun newAccessTree(scope: ResolutionHelper.AccessTreeScope.() -> Unit): ArchiveAccessTree {
                    val directTargets = ArrayList<ArchiveTarget>()
                    val transitiveTargets = ArrayList<ArchiveTarget>()

                    val scopeObject = object : ResolutionHelper.AccessTreeScope {
                        override fun direct(dependency: ClassLoadedArchiveNode<*>) {
                            directTargets.add(
                                ArchiveTarget(
                                    dependency.descriptor,
                                    ArchiveRelationship.Direct(
                                        ArchiveClassProvider(dependency.handle),
                                        ArchiveResourceProvider(dependency.handle),
                                    )
                                )
                            )

                            transitiveTargets.addAll(dependency.access.targets.map {
                                ArchiveTarget(
                                    it.descriptor,
                                    ArchiveRelationship.Transitive(
                                        it.relationship.classes,
                                        it.relationship.resources,
                                    )
                                )
                            })
                        }

                        override fun rawTarget(target: ArchiveTarget) {
                            directTargets.add(target)
                        }
                    }
                    scopeObject.scope()

                    val preliminaryTree: ArchiveAccessTree = object : ArchiveAccessTree {
                        override val descriptor: ArtifactMetadata.Descriptor = data.descriptor
                        override val targets: List<ArchiveTarget> = directTargets + transitiveTargets
                    }

                    return resolver.auditor.audit(preliminaryTree)
                }
            })().merge()
    }

    // FIXME Needs testing
    private fun doConstraints(
        tree: Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>,
        constraintPrototypes: List<Constraint<*>>
    ): Job<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = job {
        val neutrallyConstrainedTree: Tree<Tagged<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>, Constraint<*>>> =
            tree.tag {
                (it.tag.negotiator as ConstraintNegotiator<ArtifactMetadata.Descriptor, *>).constrain(it.value as IArchive<ArtifactMetadata.Descriptor>)().merge()
            }

        val list = ArrayList<Tagged<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>, Constraint<*>>>()
        neutrallyConstrainedTree.forEach(list::add)

        val allConstraints = (list.map { it.tag } + constraintPrototypes)

        val groups = allConstraints.groupBy(Constraint<*>::classifier)

        // Sanity checks
        groups.forEach { (_, v) ->
            val first = v.first() // guaranteed to be non-null by groupBy

            v.forEach {
                if (!first::class.isInstance(it)) {
                    throw IllegalStateException("Constraint type: '${it::class.java.name}' or type '${first::class.java.name}' has wrong type yet mocks constraint classifier: '${first.classifier}'.")
                }
            }
        }

        fun orderConstraintGroup(
            classifier: Any,
            group: List<Constraint<*>>,

            stack: List<Any> = listOf(),
        ): Tree<Any>? {
            if (stack.distinct().size < stack.size) {
                warning("While ordering constraints for top level archive : '${tree.item.value.descriptor}' a circular constraint group was encountered. The stack was : '$stack'. This is not a fatal error, simply ignoring this constraint group. (However this means that a dependency tree has gotten too complex and many versions are clashing, this should be simplified.)")
                return null
            }

            fun Tree<Tagged<*, Constraint<*>>>.findParent(
                const: Constraint<*>,
                parentClassifier: Any? = null,
            ): Any? {
                return if (this.item.tag.classifier == const.classifier) parentClassifier
                else parents.firstNotNullOfOrNull {
                    it.findParent(const, this.item.tag.classifier)
                }
            }

            return Tree(
                classifier,
                group.mapNotNull {
                    neutrallyConstrainedTree.findParent(it)?.let { parent ->
                        orderConstraintGroup(parent, groups[parent]!!)
                    }
                }
            )
        }

        val groupTrees = ArrayList<Tree<Any>>()

        groups.forEach { (classifier, group) ->
            if (groupTrees.any { currGroup ->
                    currGroup.find { it == classifier } != null
                }) return@forEach

            groupTrees.add(orderConstraintGroup(classifier, group) ?: return@forEach)
        }

        var orderedGroups: MutableSet<Any> = LinkedHashSet()
        groupTrees.forEach {
            it.forEachBfs { item ->
                orderedGroups.add(item)
            }
        }
        orderedGroups = orderedGroups.reversed().toMutableSet()

        // TODO allow for recalculation
        // Lazy map allows recalculation after
        val constrained = LazyMap<Any, Constraint<*>> {
            val group = groups[it]!! // Never introducing any new classifiers as the tree never adds a *new* type.
            // This means that any classifier you find in the tree will produce a non-null here.

            val negotiator = group.first().negotiator as ConstraintNegotiator<*, Constraint<*>>

            negotiator.negotiate(group)().merge()
        }

        neutrallyConstrainedTree.replace {
            val constraint = constrained[it.item.tag.classifier] ?: return@replace it

            neutrallyConstrainedTree.findBranch { it2 ->
                constraint == it2.tag
            } ?: return@replace it
        }.map {
            it.value
        }
    }

    private fun <D : ArtifactMetadata.Descriptor> isCached(
        descriptor: D,
        resolver: ArchiveNodeResolver<D, *, *, *, *>
    ): Boolean {
        if (loaded(descriptor)) {
            return true
        }

        val metadataPath = path resolve resolver.pathForDescriptor(descriptor, "archive-metadata", "json")

        return Files.exists(metadataPath)
    }

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

    private fun <
            D : ArtifactMetadata.Descriptor,
            M : ArtifactMetadata<D, *>,
            > constructArchiveTree(
        artifact: Artifact<M>,
        resolver: ArchiveNodeResolver<D, *, *, *, M>
    ): Job<AndMany<Tagged<ArchiveData<D, CacheableArchiveResource>, ArchiveNodeResolver<D, *, *, *, *>>, Tree<Tagged<ArchiveData<*, CacheableArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>>>> =
        job(
            JobName("Cache archive: '${artifact.metadata.descriptor}'") + CurrentArchive(
                artifact.metadata.descriptor
            )
        ) {
            checkRegistration(resolver)
            val trace = trace()

            if (context[ArchiveTrace]?.isCircular() == true) throw ArchiveException.CircularArtifactException(trace)

            if (!resolver.metadataType.isInstance(artifact.metadata)) throw ArchiveException(
                trace,
                "Invalid metadata type for artifact: '$artifact', expected the entire tree to be of type: '${resolver.metadataType::class.jvmName}'",
            )

            val result = (resolver as ArchiveNodeResolver<D, *, *, *, ArtifactMetadata<D, *>>)
                .cache(
                    artifact as Artifact<ArtifactMetadata<D, *>>,
                    object : CacheHelper<D> {
                        // private var parents = ArrayList<Tree<ArchiveData<*, *>>>()
                        private var resources: MutableMap<String, CacheableArchiveResource> = HashMap()

                        override fun <D : ArtifactMetadata.Descriptor, M : ArtifactMetadata<D, *>> cache(
                            artifact: Artifact<M>,
                            resolver: ArchiveNodeResolver<D, *, *, *, M>
                        ): Job<AndMany<Tagged<ArchiveData<D, CacheableArchiveResource>, ArchiveNodeResolver<D, *, *, *, *>>, Tree<Tagged<ArchiveData<*, CacheableArchiveResource>, ArchiveNodeResolver<*, *, *, *, *>>>>> =
                            constructArchiveTree(
                                artifact, resolver
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
                            parents: List<Tree<ArchiveData<*, *>>>
                        ): AndMany<ArchiveData<D, CacheableArchiveResource>, Tree<ArchiveData<*, *>>> = AndMany(
                            ArchiveData(
                                descriptor,
                                resources
                            ),
                            parents
                        )
                    }
                )().merge()

            result.mapItem {
                it.tag(resolver)
            }
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

