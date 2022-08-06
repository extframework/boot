package net.yakclient.boot.dependency

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.ArtifactGraph
import com.durganmcbroom.artifact.resolver.ArtifactResolutionOptions
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import net.yakclient.archives.ArchiveFinder
import net.yakclient.boot.archive.ArchiveNode
import net.yakclient.boot.archive.ArchiveStore
import net.yakclient.common.util.mapNotBlocking
import java.util.logging.Level
import java.util.logging.Logger

public class RepositoryArchiveLoader<O : ArtifactResolutionOptions>(
    private val artifactResolver: ArtifactGraph.ArtifactResolver<*, *, *, O>,
    private val finder: ArchiveFinder<*>,
    private val store: ArchiveStore
) {
    private val logger: Logger = Logger.getLogger(this::class.simpleName)

    public fun load(name: String, options: O.() -> Unit): ArchiveNode? =
        load(name, artifactResolver.emptyOptions().apply(options))

    public fun load(name: String, options: O): ArchiveNode? = runBlocking {
        val artifact = artifactResolver.artifactOf(name, options) ?: return@runBlocking null

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

//        if (artifactGraph.contains(desc)) return artifactGraph[desc]!!

        val cachedDeps: List<CachedArtifact> =
            cached.transitives.map { store.get(it) }.takeUnless { it.any { d -> d == null } }
                ?.filterNotNull()
                ?: throw IllegalStateException("Cached dependency: '${desc}' should already have all dependencies cached!")

        val children: Set<ArchiveNode> = cachedDeps.mapTo(HashSet()) { loadCached(it) }

//        val dependencies: List<ArchiveNode> =
//            children.filterNot { c -> children.any { it.provides(c.desc) } }

        val reference = if (cached.path != null) {
            val reference = runCatching { finder.find(cached.path) }

            if (reference.isFailure) logger.log(
                Level.SEVERE, "Failed to resolve dependency in trace : '${desc}'. Fatal error."
            )

            reference.getOrThrow()
        } else null

        return ArchiveNode(desc, reference, children)
    }

}
