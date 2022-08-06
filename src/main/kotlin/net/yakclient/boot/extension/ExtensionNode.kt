package net.yakclient.boot.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.archive.ArchiveNode
import net.yakclient.boot.container.Container
import net.yakclient.boot.dependency.DependencyNode

public data class ExtensionNode(
    override val archive: ArchiveHandle?,
    override val children: Set<ExtensionNode>,
    val dependencies: Set<DependencyNode>,
    val extension: Container<ExtensionProcess>
) : ArchiveNode
