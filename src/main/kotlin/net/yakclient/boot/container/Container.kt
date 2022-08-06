package net.yakclient.boot.container

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.container.volume.ContainerVolume

public data class Container<out T: ContainerProcess>(
    public val process: T,
    public val handle: ArchiveHandle,
    public val volume: ContainerVolume,
    public val privilegeManager: PrivilegeManager
)