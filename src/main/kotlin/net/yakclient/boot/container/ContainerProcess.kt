package net.yakclient.boot.container

import net.yakclient.archives.ArchiveHandle

public interface ContainerProcess {
    public val archive: ArchiveHandle

    public fun start()
}
