package net.yakclient.boot.dependency

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference

public fun interface ArchiveResolver : (ArchiveReference, Set<ArchiveHandle>) -> ArchiveHandle