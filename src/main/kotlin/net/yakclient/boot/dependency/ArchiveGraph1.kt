package net.yakclient.boot.dependency

import com.durganmcbroom.artifact.resolver.*
import kotlinx.coroutines.*
import net.yakclient.archives.ArchiveFinder
import net.yakclient.archives.Archives
import net.yakclient.boot.archive.ArchiveStore
import net.yakclient.common.util.mapNotBlocking
import java.util.logging.Level
import java.util.logging.Logger

public class ArchiveGraph1 internal constructor(
    private val store: ArchiveStore,
    public val defaultResolver: ArchiveResolver,
) {
    private val logger: Logger = Logger.getLogger(ArchiveGraph1::class.simpleName)

    private val artifactGraph: MutableMap<ArtifactMetadata.Descriptor, ArchiveNode> = HashMap()

    private val controller = object : ArtifactGraph.GraphController {
        override val graph: MutableMap<ArtifactMetadata.Descriptor, Artifact> = HashMap()
    }

    public fun <S : RepositorySettings, O : ArtifactResolutionOptions, D : ArtifactMetadata.Descriptor, C : ArtifactGraphConfig<D, O>> createLoader(
        provider: ArtifactGraphProvider<C, ArtifactGraph<C, S, ArtifactGraph.ArtifactResolver<D, *, S, O>>>,
        finder: ArchiveFinder<*> = Archives.Finders.JPM_FINDER,
        config: C.() -> Unit
    ): DependencyLoadingConfigurer<S, O, D> = createLoader(provider, provider.emptyConfig().apply(config), resolver)

    public fun <S : RepositorySettings, O : ArtifactResolutionOptions, D : ArtifactMetadata.Descriptor, C : ArtifactGraphConfig<D, O>> createLoader(
        provider: ArtifactGraphProvider<C, ArtifactGraph<*, S, ArtifactGraph.ArtifactResolver<D, *, S, O>>>,
        config: C,
        resolver: ArchiveResolver = defaultResolver,
    ): DependencyLoadingConfigurer<S, O, D> = createLoader(
        provider.provide(config.also { it.graph = controller }),
        resolver,
    )

    public fun <S : RepositorySettings, O : ArtifactResolutionOptions, D : ArtifactMetadata.Descriptor> createLoader(
        graph: ArtifactGraph<*, S, ArtifactGraph.ArtifactResolver<*, *, S, O>>,
        resolver: ArchiveResolver = defaultResolver,
    ): DependencyLoadingConfigurer<S, O, D> = DependencyLoadingConfigurer(graph, resolver, store)

    public inner class DependencyLoadingConfigurer<S : RepositorySettings, O : ArtifactResolutionOptions, D : ArtifactMetadata.Descriptor>(
        private val graph: ArtifactGraph<*, S, ArtifactGraph.ArtifactResolver<*, *, S, O>>,
        private val resolver: ArchiveResolver,
        private val cache: ArchiveStore,
    ) {
        public fun configureRepository(settings: S.() -> Unit): DependencyLoader<O> =
            configureRepository(graph.newRepoSettings().apply(settings))

        public fun configureRepository(settings: S): DependencyLoader<O> =
            DependencyLoader(graph.resolverFor(settings), resolver, cache)
    }

    public inner class DependencyLoader<O : ArtifactResolutionOptions> internal constructor(
        private val artifactResolver: ArtifactGraph.ArtifactResolver<*, *, *, O>,
//        private val resolver: ArchiveResolver,
        private val finder: ArchiveFinder<*>,
        private val store: ArchiveStore
    ) {
        public fun load(name: String, options: O.() -> Unit): ArchiveNode =
            load(name, artifactResolver.emptyOptions().apply(options))

        public fun load(name: String, options: O): ArchiveNode = runBlocking {
                val artifact = artifactResolver.artifactOf(name, options) ?: return@runBlocking listOf()

                cache(artifact)

                loadCached(store[artifact.metadata.desc]!!)
            }

        private suspend fun cache(
            artifact: Artifact
        ): Unit = coroutineScope {
            artifact.children.mapNotBlocking {
                cache(it)
            }

            store.cache(artifact.metadata)
        }

        private fun loadCached(cached: CachedArtifact): ArchiveNode {
            val desc = cached.desc
            logger.log(Level.INFO, "Loading dependency: '${desc}'")

            if (artifactGraph.contains(desc)) return artifactGraph[desc]!!

            val cachedDeps: List<CachedArtifact> =
                cached.transitives.map { store.get(it) }.takeUnless { it.any { d -> d == null } }
                    ?.filterNotNull()
                    ?: throw IllegalStateException("Cached dependency: '${desc}' should already have all dependencies cached!")

            val children: Set<ArchiveNode> = cachedDeps.mapTo(HashSet()) { loadCached(it) }

            val dependencies: List<ArchiveNode> = children.filterNot { c -> children.any { it.provides(c.desc) } }

            val reference = if (cached.path != null) {
                val reference = runCatching { finder.find(cached.path) }

                if (reference.isFailure) logger.log(
                    Level.SEVERE, "Failed to resolve dependency in trace : '${desc}'. Fatal error."
                )

                reference.getOrThrow()
            } else null

            return ArchiveNode(desc, reference, children).also {
                artifactGraph[it.desc] = it
            }
        }

//        private fun loadReference(
//            path: Path, dependencies: List<ArchiveNode>
//        ) = resolver(
//            Archives.find(path, Archives.Finders.JPM_FINDER),
//            dependencies.flatMapTo(HashSet(), ArchiveNode::referenceOrChildren)
//        )
    }


}



