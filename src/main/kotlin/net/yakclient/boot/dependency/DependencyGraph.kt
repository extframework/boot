package net.yakclient.boot.dependency

import asOutput
import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.JobResult
import com.durganmcbroom.jobs.JobResult.Success
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.logging.info
import kotlinx.coroutines.async
import net.yakclient.archives.ResolutionResult
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.archive.ArchiveLoadException
import net.yakclient.boot.archive.ArchiveResolutionProvider
import net.yakclient.boot.archive.handleOrChildren
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.store.DataStore
import net.yakclient.boot.util.toSafeResource
import net.yakclient.common.util.resource.SafeResource
import withWeight
import java.nio.file.Path

public abstract class DependencyGraph<K : ArtifactMetadata.Descriptor, in R : RepositorySettings>(
    private val store: DataStore<K, DependencyData<K>>,
    public override val repositoryFactory: RepositoryFactory<R, out ArtifactRequest<K>, *, *, *>,
    private val archiveResolver: ArchiveResolutionProvider<ResolutionResult>,
    private val mutableGraph: MutableMap<K, DependencyNode> = HashMap(),
    private val privilegeManager: PrivilegeManager = PrivilegeManager(null, PrivilegeAccess.emptyPrivileges()) {},
) : ArchiveGraph<K, DependencyNode, R>(
    repositoryFactory
) {
    override val graph: Map<K, DependencyNode>
        get() = mutableGraph.toMap()

    abstract override fun cacherOf(settings: R): DependencyCacher<out ArtifactRequest<K>, *>

    override fun isCached(descriptor: K): Boolean = store.contains(descriptor)

    override suspend fun get(descriptor: K): JobResult<DependencyNode, ArchiveLoadException> {
        return mutableGraph[descriptor]?.let(::Success) ?: run {
            val data = store[descriptor] ?: return JobResult.Failure(ArchiveLoadException.ArtifactNotCached)

            val context = object : GraphContext<K> {
                override fun get(key: K): DependencyNode? = mutableGraph[key]

                override fun put(key: K, node: DependencyNode) {
                    mutableGraph[key] = node
                }
            }

            load(context, data)
        }
    }


//    public fun interface DependencyPrePopulationProvider {
//        public fun provideAll(): List<DependencyNode>
//    }

    //    protected abstract inner class DependencyPrePopulator(
//    ) : ArchiveLoader<S>(
//        ResolutionContext(
//            object : ArtifactRepositoryContext<K, S, ArtifactReference<*, S>> {
//                override val artifactRepository: Nothing
//                    get() = throw IllegalStateException("This is an unusable context.")
//            },
//            object : StubResolverContext<S, ArtifactReference<*, S>> {
//                override val stubResolver: Nothing
//                    get() = throw IllegalStateException("This is an unusable context.")
//            },
//            object : ArtifactComposerContext {
//                override val artifactComposer: Nothing
//                    get() = throw IllegalStateException("This is an unusable context.")
//            }
//        )
//    ) {
//        override fun load(request: K): Either<ArchiveLoadException, DependencyNode> =
//            ArchiveLoadException.NotSupported.left()
//
//        public abstract fun prePopulate()
//    }
    public interface GraphContext<K : ArtifactMetadata.Descriptor> {
        public operator fun get(key: K): DependencyNode?

        public fun put(key: K, node: DependencyNode)
    }

    protected open suspend fun load(
        context: GraphContext<K>,
        data: DependencyData<K>,
    ): JobResult<DependencyNode, ArchiveLoadException> = job(JobName("Load dependency: '${data.key.name}'")) {
        context[data.key] ?: run {
            val children = data.children
                .map(store::get)
                .map { it ?: fail(ArchiveLoadException.ArchiveGraphInvalidated) }
                .map { dependencyData ->
                    async {
                        withWeight(1) {
                            load(context, dependencyData)
                        }
                    }
                }.mapTo(HashSet()) { it.await().attempt() }

            val resource = data.archive ?: return@job DependencyNode(null, children)

            val handles = children.flatMapTo(HashSet()) { it.handleOrChildren() }

            val handle = archiveResolver.resolve(resource, {
                DependencyClassLoader(it, handles, privilegeManager)
            }, handles).attempt()

            DependencyNode(handle.archive, children).also {
                context.put(data.key, it)
            }
        }
    }

    protected abstract suspend fun writeResource(descriptor: K, resource: SafeResource): Path

    public abstract inner class DependencyCacher<E : ArtifactRequest<K>, S : ArtifactStub<E, *>>(
        resolver: ResolutionContext<E, S, ArtifactReference<*, S>>,
    ) : ArchiveCacher<E, S>(
        resolver,
    ) {
        override suspend fun cache(request: E): JobResult<Unit, ArchiveLoadException> {
            if (!mutableGraph.contains(request.descriptor) && !store.contains(request.descriptor)) {
                val unboundRef = resolver.repositoryContext.artifactRepository
                    .get(request).asOutput()

                val ref = unboundRef
                    .mapFailure(ArchiveLoadException::ArtifactLoadException)

                if (ref.wasFailure()) return JobResult.Failure(ref.failureOrNull()!!)

                return cache(request, ref.orNull()!!, ArtifactTrace(request, null)).map { }
            }

            return Success(Unit)
        }


//        private fun cache(
//            request: K,
//        ): Either<ArchiveLoadException, Unit> {
//            return store[request]?.right() ?: either.eager {
//                val unboundRef = resolver.repositoryContext.artifactRepository
//                    .get(request)
//
//                val ref = unboundRef
//                    .mapLeft(ArchiveLoadException::ArtifactLoadException)
//                    .bind()
//
//                cache(request, ref).bind()
//            }
//        }


        private suspend fun cache(
            request: E,
            ref: ArtifactReference<*, S>,
            trace: ArtifactTrace<E>,
        ): JobResult<DependencyData<K>, ArchiveLoadException> =
            job(JobName("Cache dependency: '${request.descriptor.name}'")) {
                if (trace.isCircular()) {
                    fail(ArchiveLoadException.CircularArtifactException(trace.toList().map { it.descriptor }))
                }

                if (store.contains(request.descriptor)) {
                    info("Dependency: '${request.descriptor}' already cached, returning early.")
                    return@job store[request.descriptor]!!
                }

                ref.children.map { stub ->
                    val resolve = resolver.resolverContext.stubResolver
                        .resolve(stub).asOutput()

                    val childRef = resolve
                        .mapFailure(ArchiveLoadException::ArtifactLoadException)
                        .attempt()

                    async {
                        withWeight(1) {
                            cache(stub.request, childRef, trace.child(stub.request))
                        }
                    }
                }.forEach { it.await().attempt() }

                val data = DependencyData(
                    request.descriptor,
                    ref.metadata.resource?.toSafeResource()
                        ?.let { writeResource(request.descriptor, it) },
                    ref.children.map { it.request }.map { it.descriptor }
                )
                store.put(
                    request.descriptor, data
                )

                data
            }

//        protected abstract inner class LocalGraph {
//            private val localGraph: MutableMap<VersionIndependentDependencyKey, DependencyNode> = HashMap()
//            public val graphContext: GraphContext<K> = object : GraphContext<K> {
//                override fun get(key: K): DependencyNode? {
//                    val keyFor = getKey(request)
//
//                    return localGraph[keyFor] ?: mutableGraph[request]?.also {
//                        localGraph[keyFor] = it
//                    }
//                }
//
//                override fun put(key: ArchiveKey<K>, node: DependencyNode) {
//                    val request by key::request
//
//                    localGraph[getKey(request)] = node
//                    mutableGraph[ArchiveKey(request)] = node
//                }
//            }
//
//            protected abstract fun getKey(request: K): VersionIndependentDependencyKey
//        }
    }

    private data class ArtifactTrace<E : ArtifactRequest<*>>(
        val request: E,
        val parent: ArtifactTrace<E>?
    ) {
        fun child(request: E) = ArtifactTrace(request, this)

        fun isCircular(toCheck: List<E> = listOf()): Boolean {
            return toCheck.any { it.descriptor == request.descriptor } || parent?.isCircular(toCheck + request) == true
        }

        fun toList(): List<E> {
            return (parent?.toList() ?: listOf()) + request
        }

        override fun toString(): String {
            return toList().joinToString(separator = " -> ") { it.descriptor.toString() }
        }
    }
}