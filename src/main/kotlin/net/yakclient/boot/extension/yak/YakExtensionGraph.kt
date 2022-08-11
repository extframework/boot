package net.yakclient.boot.extension.yak

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.ArtifactGraph
import net.yakclient.boot.dependency.DependencyStore
import net.yakclient.boot.dependency.DependencyGraph
import net.yakclient.boot.extension.*
import net.yakclient.boot.extension.yak.artifact.YakExtArtifactMetadata
import net.yakclient.boot.extension.yak.artifact.YakExtArtifactResolutionOptions

public class YakExtensionGraph(
    store: ExtensionStore,
    private val dependencyGraph: DependencyGraph
) : ExtensionGraph(store) {


    public inner class YakExtGraphPopulator(
        override val resolver: ArtifactGraph.ArtifactResolver<*, *, *, YakExtArtifactResolutionOptions>,
        loader: ExtensionLoader<YakExtensionInfo>
    ) : ExtensionGraphPopulator<YakExtArtifactResolutionOptions, YakExtensionInfo>(resolver, loader) {
        override fun load(data: ExtensionData): ExtensionNode {
            TODO("Not yet implemented")
        }

        override fun cache(artifact: Artifact) {

            //            check(artifact.metadata is YakExtArtifactMetadata) { "Invalid artifact! The base artifact must be a YakClient extension!" }
            //
            //            val (c, d) = artifact.children.partition { it.metadata is YakExtArtifactMetadata }
            //
            //            val dependencies = d.map { dependencyGraph.load(it) }
            //            val children = c.map(::load)
            //
            //            val info =
            //            val process = loader.load()
        }


    }
}