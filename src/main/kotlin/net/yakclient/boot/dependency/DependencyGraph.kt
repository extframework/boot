package net.yakclient.boot.dependency

import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.right
import com.durganmcbroom.artifact.resolver.*
import net.yakclient.archives.*
import net.yakclient.boot.archive.*
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.store.DataStore
import net.yakclient.boot.toSafeResource
import net.yakclient.common.util.resource.SafeResource
import java.nio.file.Path

public abstract class DependencyGraph<K : ArtifactRequest<*>, S : ArtifactStub<K, *>, in R : RepositorySettings>(
    private val store: DataStore<K, DependencyData<K>>,
    public override val repositoryFactory: RepositoryFactory<R, K, S, *, *>,
    private val archiveResolver: ArchiveResolutionProvider<ResolutionResult>,
    private val mutableGraph: MutableMap<ArchiveKey<K>, DependencyNode> = HashMap(),
    private val privilegeManager: PrivilegeManager = PrivilegeManager(null, PrivilegeAccess.emptyPrivileges()) {},
) : ArchiveGraph<K, DependencyNode, R>(
    repositoryFactory
) {
    override val graph: Map<ArchiveKey<K>, DependencyNode>
        get() = mutableGraph.toMap()

    abstract override fun cacherOf(settings: R): ArchiveCacher<*>

    override fun get(request: K): Either<ArchiveLoadException, DependencyNode> {
        val key = ArchiveKey(request)

        return mutableGraph[key]?.right() ?: either.eager {
            val data = store[key.request] ?: shift(ArchiveLoadException.ArtifactNotCached)

            val context = object : GraphContext<K> {
                override fun get(key: ArchiveKey<K>): DependencyNode? = mutableGraph[key]

                override fun put(key: ArchiveKey<K>, node: DependencyNode) {
                    mutableGraph[key] = node
                }
            }

            load(context, data).bind()
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
    public interface GraphContext<K : ArtifactRequest<*>> {
        public operator fun get(key: ArchiveKey<K>): DependencyNode?

        public fun put(key: ArchiveKey<K>, node: DependencyNode)
    }


    private fun load(
        context: GraphContext<K>,
        data: DependencyData<K>,
    ): Either<ArchiveLoadException, DependencyNode> = either.eager {
        context[ArchiveKey(data.key)] ?: either.eager eagerLoadFromData@{
            val children = data.children
                .map(store::get)
                .map { it ?: shift(ArchiveLoadException.ArchiveGraphInvalidated) }
                .map { load(context, it) }
                .mapTo(HashSet()) { it.bind() }

            val resource = data.archive ?: return@eagerLoadFromData DependencyNode(null, children)

            val handles = children.flatMapTo(HashSet()) { it.handleOrChildren() }

            val handle = archiveResolver.resolve(resource, {
                DependencyClassLoader(it, handles, privilegeManager)
            }, handles).bind().archive

            DependencyNode(handle, children).also {
                context.put(ArchiveKey(data.key), it)
            }
        }.bind()
    }

    protected abstract fun writeResource(request: K, resource: SafeResource): Path

    public abstract inner class DependencyCacher(
        resolver: ResolutionContext<K, S, ArtifactReference<*, S>>,
    ) : ArchiveCacher<S>(
        resolver,
    ) {
        protected abstract fun newLocalGraph(): LocalGraph

        override fun cache(request: K): Either<ArchiveLoadException, Unit> = either.eager {
            val key = ArchiveKey(request)

            if (!mutableGraph.contains(key) && !store.contains(request)) {
                val unboundRef = resolver.repositoryContext.artifactRepository
                    .get(request)

                val ref = unboundRef
                    .mapLeft(ArchiveLoadException::ArtifactLoadException)
                    .bind()

                cache(request, ref).bind()
            }
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

        private fun cache(
            request: K,
            ref: ArtifactReference<*, S>,
        ): Either<ArchiveLoadException, DependencyData<K>> = either.eager {
            ref.children.forEach { stub ->
                val resolve = resolver.resolverContext.stubResolver
                    .resolve(stub)

                val childRef = resolve
                    .mapLeft(ArchiveLoadException::ArtifactLoadException)
                    .bind()

                cache(stub.request, childRef).bind()
            }

            val data = DependencyData(
                request,
                ref.metadata.resource?.toSafeResource()?.let { writeResource(request, it) },
                ref.children.map(ArtifactStub<K, *>::request)
            )

            if (!store.contains(request)) store.put(
                request, data
            )

            data
        }

        protected abstract inner class LocalGraph {
            private val localGraph: MutableMap<VersionIndependentDependencyKey, DependencyNode> = HashMap()
            public val graphContext: GraphContext<K> = object : GraphContext<K> {
                override fun get(key: ArchiveKey<K>): DependencyNode? {
                    val request by key::request

                    val keyFor = getKey(request)

                    return localGraph[keyFor] ?: mutableGraph[ArchiveKey(request)]?.also {
                        localGraph[keyFor] = it
                    }
                }

                override fun put(key: ArchiveKey<K>, node: DependencyNode) {
                    val request by key::request

                    localGraph[getKey(request)] = node
                    mutableGraph[ArchiveKey(request)] = node
                }
            }

            protected abstract fun getKey(request: K): VersionIndependentDependencyKey
        }
    }
}