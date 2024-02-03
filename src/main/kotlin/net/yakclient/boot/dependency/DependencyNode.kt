package net.yakclient.boot.dependency

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.archive.ArchiveAccessTree
import net.yakclient.boot.archive.ArchiveNode
import net.yakclient.boot.archive.ArchiveNodeResolver

public abstract class DependencyNode<T : DependencyNode<T>>: ArchiveNode<T> {
    override fun toString(): String {
        return descriptor.name
    }
}

public class BasicDependencyNode(
    override val descriptor: ArtifactMetadata.Descriptor,
    override val archive: ArchiveHandle?,
    override val parents: Set<BasicDependencyNode>,
    override val access: ArchiveAccessTree,
    override val resolver: ArchiveNodeResolver<*, *, BasicDependencyNode, *, *>
) : DependencyNode<BasicDependencyNode>()

//public open class DependencyNode(
//    override val archive: ArchiveHandle?,
//    override val parents: Set<DependencyNode>,
//    override val descriptor: ArtifactMetadata.Descriptor,
//    override val access: ArchiveAccessTree,
//    override val resolver: ArchiveNodeResolver<*, *, DependencyNode, *, *>,
//) : ArchiveNode<DependencyNode> {
//    override fun toString(): String {
//        return descriptor.name
//    }
//}