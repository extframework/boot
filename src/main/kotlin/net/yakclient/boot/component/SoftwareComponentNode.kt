package net.yakclient.boot.component

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.archive.ArchiveNode
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor

public data class SoftwareComponentNode(
    val descriptor: SoftwareComponentDescriptor,
    override val archive: ArchiveHandle?,
    override val children: Set<SoftwareComponentNode>,
    val dependencies: Set<DependencyNode>,
    val model: SoftwareComponentModel,
    val factory: ComponentFactory<*, *>?,
) : ArchiveNode