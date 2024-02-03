package net.yakclient.boot.component

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.archive.ArchiveAccessTree
import net.yakclient.boot.archive.ArchiveNode
import net.yakclient.boot.archive.ArchiveNodeResolver
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor

public data class SoftwareComponentNode(
    override val descriptor: SoftwareComponentDescriptor,
    override val archive: ArchiveHandle?,
    override val parents: Set<SoftwareComponentNode>,

    val dependencies: Set<DependencyNode<*>>,
    val model: SoftwareComponentModel,
    val factory: ComponentFactory<*, *>?,

    override val access: ArchiveAccessTree,
    override val resolver: ArchiveNodeResolver<*, *, SoftwareComponentNode, *, *>,
) : DependencyNode<SoftwareComponentNode>() {
    override fun toString(): String {
        return descriptor.name
    }
}