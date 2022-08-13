package net.yakclient.boot.extension.yak

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.archive.ArchiveNode
import net.yakclient.boot.container.Container
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.boot.extension.ExtensionNode
import net.yakclient.boot.extension.ExtensionProcess

public data class YakExtensionNode(
    override val archive: ArchiveHandle?,
    override val children: Set<ArchiveNode>,
    override val dependencies: Set<DependencyNode>,
    override val extension: Container<ExtensionProcess>,
    val erm: YakErm
) : ExtensionNode