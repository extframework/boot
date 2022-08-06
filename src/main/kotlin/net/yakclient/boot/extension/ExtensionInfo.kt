package net.yakclient.boot.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.container.Container
import net.yakclient.boot.container.ContainerInfo

public interface ExtensionInfo : ContainerInfo {
    public val archive: ArchiveReference
    public val extensionDependencies: List<Container<ExtensionProcess>>
    public val dependencies: List<ArchiveHandle>
}
