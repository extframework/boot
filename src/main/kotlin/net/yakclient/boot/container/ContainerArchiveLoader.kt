package net.yakclient.boot.container

import com.durganmcbroom.jobs.JobResult
import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.archive.ArchiveException

public interface ContainerArchiveLoader<in I: ArchiveContainerInfo> {
    public suspend fun load(info: I) : JobResult<ArchiveHandle, ArchiveException>
}