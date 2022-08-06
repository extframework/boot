package net.yakclient.boot.archive

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference

public fun interface ArchiveLoader<out O : ArchiveLoader.PostLoadedArchive> {
    public fun load(archive: ArchiveReference, parents: Set<ArchiveHandle>): O

    public interface PostLoadedArchive {
        public val archive: ArchiveHandle
    }
}