package net.yakclient.boot.extension

import com.durganmcbroom.artifact.resolver.*
import net.yakclient.boot.RepositoryArchiveGraph
import net.yakclient.boot.archive.ArchiveDescriptor
import net.yakclient.boot.archive.ArchiveStore

public abstract class ExtensionGraph(store: ArchiveStore) : RepositoryArchiveGraph<ExtensionNode>(store) {
    private val _graph: MutableMap<ArchiveDescriptor, ExtensionNode> = HashMap()
    override val graph: Map<ArchiveDescriptor, ExtensionNode>
        get() = _graph.toMap()

//    public fun <S : RepositorySettings, O : ArtifactResolutionOptions, D : ArtifactMetadata.Descriptor, C : ArtifactGraphConfig<D, O>> createLoader(
//        provider: ArtifactGraphProvider<C, ArtifactGraph<C, S, ArtifactGraph.ArtifactResolver<D, *, S, O>>>,
//        config: C.() -> Unit
//    ): RepositoryConfigurer<S, O> = createLoader(provider, provider.emptyConfig().apply(config))
//
//    public fun <S : RepositorySettings, O : ArtifactResolutionOptions, D : ArtifactMetadata.Descriptor, C : ArtifactGraphConfig<D, O>> createLoader(
//        provider: ArtifactGraphProvider<C, ArtifactGraph<*, S, ArtifactGraph.ArtifactResolver<D, *, S, O>>>,
//        config: C,
//    ): RepositoryConfigurer<S, O> = createLoader(
//        provider.provide(config.also { it.graph = controller }),
//    )
//
//    public fun <S : RepositorySettings, O : ArtifactResolutionOptions> createLoader(
//        graph: ArtifactGraph<*, S, ArtifactGraph.ArtifactResolver<*, *, S, O>>,
//    ): RepositoryConfigurer<S, O> = RepositoryConfigurer(graph)
//
//    public fun <O : ArtifactResolutionOptions> createLoader(resolver: ArtifactGraph.ArtifactResolver<*, *, *, O>): RepositoryGraphPopulator<O> =
//        DependencyGraphPopulator(resolver)

//    public inner class RepositoryConfigurer<S : RepositorySettings, O : ArtifactResolutionOptions>(
//        private val graph: ArtifactGraph<*, S, ArtifactGraph.ArtifactResolver<*, *, S, O>>,
//    ) {
//        public fun configureRepository(settings: S.() -> Unit): RepositoryGraphPopulator<O> =
//            configureRepository(graph.newRepoSettings().apply(settings))
//
//        public fun configureRepository(settings: S): RepositoryGraphPopulator<O> =
//            createLoader(graph.resolverFor(settings))
//    }


    public abstract inner class ExtensionGraphPopulator<O: ArtifactResolutionOptions, T: ExtensionInfo>(
        resolver: ArtifactGraph.ArtifactResolver<*, *, *, O>,
        protected val loader: ExtensionLoader<T>
    ) : RepositoryGraphPopulator<O>(
        resolver
    ) {

    }
}
