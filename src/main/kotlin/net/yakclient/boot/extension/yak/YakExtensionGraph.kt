package net.yakclient.boot.extension.yak

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.ArtifactGraph
import net.yakclient.boot.DescriptorKey
import net.yakclient.boot.dependency.DependencyGraph
import net.yakclient.boot.extension.ExtensionGraph
import net.yakclient.boot.extension.ExtensionLoader
import net.yakclient.boot.extension.ExtensionNode
import net.yakclient.boot.extension.ExtensionStore
import net.yakclient.boot.extension.yak.artifact.YakExtArtifactMetadata
import net.yakclient.boot.extension.yak.artifact.YakExtArtifactResolutionOptions
import java.nio.file.Path

//public class YakExtensionGraph(
//    path: Path,
//    private val dependencyGraph: DependencyGraph
//) : ExtensionGraph<YakExtensionNode, YakExtensionData>(ExtensionStore(YakExtensionDataAccess(path))) {
//
//
//
//    private inner class YakExtGraphPopulator(
//        loader: ExtensionLoader<YakExtensionInfo>
//    ) : ExtensionGraphPopulator<YakExtArtifactResolutionOptions, YakExtensionInfo>(resolver, loader) {
//
//
//        //            check(artifact.metadata is YakExtArtifactMetadata) { "Invalid artifact! The base artifact must be a YakClient extension!" }
//        //
//        //            val (c, d) = artifact.children.partition { it.metadata is YakExtArtifactMetadata }
//        //
//        //            val dependencies = d.map { dependencyGraph.load(it) }
//        //            val children = c.map(::load)
//        //
//        //            val info =
//        //            val process = loader.load()
//        override fun load(name: String, options: YakExtArtifactResolutionOptions): YakExtensionNode? {
//            TODO("Not yet implemented")
//        }
//
//        private fun cache(artifact: Artifact) {
//            val meta = artifact.metadata as? YakExtArtifactMetadata
//                ?: throw IllegalArgumentException("Invalid artifact: '$artifact'. Its metadata type must be '${YakExtArtifactMetadata::class.simpleName}'.")
//
//            val key = DescriptorKey(meta.desc)
//            if (store.contains(key)) return
//
//            val erm = meta.erm
//            store.put(
//                key,
//                YakExtensionData(
//                    key,
//                    erm.extensionDependencies.map {  }
//                )
//            )
//        }
//    }
//}