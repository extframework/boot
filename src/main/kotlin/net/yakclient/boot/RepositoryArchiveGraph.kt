package net.yakclient.boot

import com.durganmcbroom.artifact.resolver.*
import net.yakclient.boot.archive.*
import net.yakclient.boot.store.DataStore

public abstract class RepositoryArchiveGraph<N : ArchiveNode, V : ArchiveData>(
    store: DataStore<ArtifactArchiveKey, V>,
) : ArchiveGraph<N, ArtifactArchiveKey, V>(store) {
//    public fun <S : RepositorySettings, O : ArtifactResolutionOptions, D : ArtifactMetadata.Descriptor, C : ArtifactGraphConfig<D, O>> createLoader(
//        provider: ArtifactGraphProvider<C, ArtifactGraph<*, S, ArtifactGraph.ArtifactResolver<D, *, S, O>>>,
//    ): RepositoryConfigurer<S, O> = createLoader(
//        groupGraph[provider]
//            ?: throw IllegalArgumentException("Unknown Graph provider: '${provider::class.simpleName}', please register this provider first with : '$")
//    )

//    public open fun <D : ArtifactMetadata.Descriptor, O : ArtifactResolutionOptions, C : ArtifactGraphConfig<D, O>> register(
//        provider: ArtifactGraphProvider<C, ArtifactGraph<C, *, ArtifactGraph.ArtifactResolver<D, *, *, O>>>
//    ): ResolutionGroupConfig.ResolutionBuilder<D, O, C> = groupConfig.graphOf(provider)

    //    public fun <S : RepositorySettings, O : ArtifactResolutionOptions> createLoader(
//        graph: ArtifactGraph<*, S, ArtifactGraph.ArtifactResolver<*, *, S, O>>,
//    ): RepositoryConfigurer<S, O> = RepositoryConfigurer(graph)
//
//    protected abstract fun <O : ArtifactResolutionOptions> createLoader(resolver: ArtifactGraph.ArtifactResolver<*, *, *, O>): RepositoryGraphPopulator<O>
//
//    public abstract inner class RepositoryConfigurer<S : RepositorySettings, O : ArtifactResolutionOptions> protected constructor(
//        private val graph: ArtifactGraph<*, S, ArtifactGraph.ArtifactResolver<*, *, S, O>>,
//    ) {
//        public fun configureRepository(settings: S.() -> Unit): RepositoryGraphPopulator<O> =
//            configureRepository(graph.newRepoSettings().apply(settings))
//
//        public abstract fun configureRepository(settings: S): RepositoryGraphPopulator<O>
//    }

    public abstract inner class RepositoryGraphPopulator<O : ArtifactResolutionOptions>(
        protected open val resolver: ArtifactGraph.ArtifactResolver<*, *, *, O>,
    ) : GraphPopulator() {
        public fun emptyOptions() : O = resolver.emptyOptions()

        override fun load(name: String): N? = load(name, resolver.emptyOptions())

        public open fun load(name: String, options: O): N? {
            val key = ArtifactArchiveKey(resolver.descriptorOf(name) ?: return null)

            return graph[key] ?: load(
                store[key]
                    ?: resolver.artifactOf(name, options)?.let(::cache)?.let { store[key] }
                    ?: return null
            )
        }

        public abstract fun cache(artifact: Artifact)

        public abstract fun load(data: V): N
//        public open fun load(name: String, artifactResolutionOptions: O): N? {
//            val desc: ArtifactMetadata.Descriptor = resolver.descriptorOf(name) ?: return null
//            return graph[ArtifactArchiveDescriptor(desc)] ?: store.get(desc) ?: run {
//                @Suppress(CAST)
//                val artifact = (resolver as ArtifactGraph.ArtifactResolver<ArtifactMetadata.Descriptor, *, *, O>)
//                    .artifactOf(desc, artifactResolutionOptions) ?: return null
//
//                return load(artifact)
//            }
//        }
//
//        protected abstract fun load(artifact: Artifact): N?
    }

}