package net.yakclient.boot.archive

import net.yakclient.archives.ArchiveHandle

public interface ArchiveNode {
    public val archive: ArchiveHandle?
    public val children: Set<ArchiveNode>
}

public fun ArchiveNode.handleOrChildren(): Set<ArchiveHandle> =
    archive?.let(::setOf) ?: children.flatMapTo(HashSet()) { it.handleOrChildren() }