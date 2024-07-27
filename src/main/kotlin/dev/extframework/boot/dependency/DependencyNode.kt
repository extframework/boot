package dev.extframework.boot.dependency

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import dev.extframework.archives.ArchiveHandle
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveNode
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.archive.ClassLoadedArchiveNode

public abstract class DependencyNode<T : ArtifactMetadata.Descriptor>: ClassLoadedArchiveNode<T> {
    override fun toString(): String {
        return descriptor.name
    }
}

public data class BasicDependencyNode<T: ArtifactMetadata.Descriptor>(
    override val descriptor: T,
    override val handle: ArchiveHandle?,
    override val access: ArchiveAccessTree,
) : DependencyNode<T>()

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