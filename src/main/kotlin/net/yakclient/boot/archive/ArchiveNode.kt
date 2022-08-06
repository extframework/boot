package net.yakclient.boot.archive

import net.yakclient.archives.ArchiveHandle

public interface ArchiveNode {
    public val archive: ArchiveHandle?
    public val children: Set<ArchiveNode>
}