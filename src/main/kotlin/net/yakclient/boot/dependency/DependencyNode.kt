package net.yakclient.boot.dependency

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.archive.ArchiveNode

public data class DependencyNode(
    override val archive: ArchiveHandle?,
    override val children: Set<DependencyNode>,
) : ArchiveNode

public fun DependencyNode.handleOrChildren(): Set<ArchiveHandle> =
    archive?.let(::setOf) ?: children.flatMapTo(HashSet()) { it.handleOrChildren() }