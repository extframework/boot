package net.yakclient.boot.extension.yak

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.ArtifactGraph
import net.yakclient.boot.archive.ArchiveStore
import net.yakclient.boot.dependency.DependencyGraph
import net.yakclient.boot.extension.ExtensionGraph
import net.yakclient.boot.extension.ExtensionLoader
import net.yakclient.boot.extension.ExtensionNode
import net.yakclient.boot.extension.yak.artifact.YakExtArtifactGraph
import net.yakclient.boot.extension.yak.artifact.YakExtArtifactMetadata
import net.yakclient.boot.extension.yak.artifact.YakExtArtifactResolutionOptions

public class YakExtensionGraph(
    store: ArchiveStore,
    private val dependencyGraph: DependencyGraph
) : ExtensionGraph(store) {


    public inner class YakExtGraphPopulator(
        override val resolver: ArtifactGraph.ArtifactResolver<*, *, *, YakExtArtifactResolutionOptions>,
        loader: ExtensionLoader<YakExtensionInfo>
    ) : ExtensionGraphPopulator<YakExtArtifactResolutionOptions, YakExtensionInfo>(resolver, loader) {
        override fun load(artifact: Artifact): ExtensionNode? {


            check(artifact.metadata is YakExtArtifactMetadata) { "Invalid artifact! The base artifact must be a YakClient extension!" }

            val (c, d) = artifact.children.partition { it.metadata is YakExtArtifactMetadata }

            val dependencies = d.map { dependencyGraph.load(it) }
            val children = c.map(::load)

            val info =
            val process = loader.load()
        }


    }
}