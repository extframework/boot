package net.yakclient.boot

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.group.GroupedArtifactGraph
import com.durganmcbroom.artifact.resolver.group.ResolutionGroup
import com.durganmcbroom.artifact.resolver.group.ResolutionGroupConfig
import net.yakclient.boot.archive.*
import net.yakclient.common.util.CAST

public abstract class RepositoryArchiveGraph<T : ArchiveNode>(
    store: ArchiveStore,
    private val groupConfig: ResolutionGroupConfig
) : ArchiveGraph<T>(store) {
    protected val groupGraph: GroupedArtifactGraph = ResolutionGroup.provide(groupConfig)

//    public fun <S : RepositorySettings, O : ArtifactResolutionOptions, D : ArtifactMetadata.Descriptor, C : ArtifactGraphConfig<D, O>> createLoader(
//        provider: ArtifactGraphProvider<C, ArtifactGraph<*, S, ArtifactGraph.ArtifactResolver<D, *, S, O>>>,
//    ): RepositoryConfigurer<S, O> = createLoader(
//        groupGraph[provider]
//            ?: throw IllegalArgumentException("Unknown Graph provider: '${provider::class.simpleName}', please register this provider first with : '$")
//    )

    public fun <D : ArtifactMetadata.Descriptor, O : ArtifactResolutionOptions, C : ArtifactGraphConfig<D, O>> register(
        provider: ArtifactGraphProvider<C, ArtifactGraph<C, *, ArtifactGraph.ArtifactResolver<D, *, *, O>>>
    ): ResolutionGroupConfig.ResolutionBuilder<D, O, C> = groupConfig.graphOf(provider)

//    public fun <S : RepositorySettings, O : ArtifactResolutionOptions> createLoader(
//        graph: ArtifactGraph<*, S, ArtifactGraph.ArtifactResolver<*, *, S, O>>,
//    ): RepositoryConfigurer<S, O> = RepositoryConfigurer(graph)
//
//    protected abstract fun <O : ArtifactResolutionOptions> createLoader(resolver: ArtifactGraph.ArtifactResolver<*, *, *, O>): RepositoryGraphPopulator<O>
//
//    public inner class RepositoryConfigurer<S : RepositorySettings, O : ArtifactResolutionOptions>(
//        private val graph: ArtifactGraph<*, S, ArtifactGraph.ArtifactResolver<*, *, S, O>>,
//    ) {
//        public fun configureRepository(settings: S.() -> Unit): RepositoryGraphPopulator<O> =
//            configureRepository(graph.newRepoSettings().apply(settings))
//
//        public fun configureRepository(settings: S): RepositoryGraphPopulator<O> =
//            createLoader(graph.resolverFor(settings))
//    }

    public abstract inner class RepositoryGraphPopulator<O : ArtifactResolutionOptions>(
        protected open val resolver: ArtifactGraph.ArtifactResolver<*, *, *, O>,
    ) : GraphPopulator() {
        override fun load(name: String): T? = load(name, resolver.emptyOptions())

        public open fun load(name: String, artifactResolutionOptions: O): T? {
            val desc: ArtifactMetadata.Descriptor = resolver.descriptorOf(name) ?: return null
            return graph[ArtifactArchiveDescriptor(desc)] ?: store.get(desc) ?: run {
                @Suppress(CAST)
                val artifact = (resolver as ArtifactGraph.ArtifactResolver<ArtifactMetadata.Descriptor, *, *, O>)
                    .artifactOf(desc, artifactResolutionOptions) ?: return null

                return load(artifact)
            }
        }

        protected abstract fun load(artifact: Artifact): T?
    }

    public data class ArtifactArchiveDescriptor(
        public val desc: ArtifactMetadata.Descriptor
    ) : ArchiveDescriptor
}