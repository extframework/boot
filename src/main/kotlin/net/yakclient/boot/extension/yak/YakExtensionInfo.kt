package net.yakclient.boot.extension.yak

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.container.Container
import net.yakclient.boot.extension.ExtensionInfo
import net.yakclient.boot.extension.ExtensionProcess

public data class YakExtensionInfo(
    override val archive: ArchiveReference,
    override val extensionDependencies: List<Container<ExtensionProcess>>,
    override val dependencies: List<ArchiveHandle>,
    val erm: YakErm,
) : ExtensionInfo