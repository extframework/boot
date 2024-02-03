package net.yakclient.boot.container

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.container.volume.ContainerVolume

public data class ArchiveContainer(
//    public val process: T,
//    public val descriptor: ArtifactMetadata.Descriptor,

    public val handle: ArchiveHandle,

    // Decide if these two are needed
    public val volume: ContainerVolume,
    public val privilegeManager: PrivilegeManager
)