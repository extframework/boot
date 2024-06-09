package dev.extframework.boot.component

import dev.extframework.archives.ArchiveHandle
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.dependency.DependencyNode
import dev.extframework.boot.component.artifact.SoftwareComponentDescriptor

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