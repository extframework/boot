package com.kaolinmc.boot.dependency

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.kaolinmc.archives.ArchiveHandle
import com.kaolinmc.boot.archive.ArchiveAccessTree
import com.kaolinmc.boot.archive.ClassLoadedArchiveNode

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