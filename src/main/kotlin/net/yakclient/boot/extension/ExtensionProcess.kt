package net.yakclient.boot.extension

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.container.ContainerProcess

public data class ExtensionProcess(
    val extension: Extension,
    val stateHolder: ExtensionStateHolder,
    val handle: ArchiveHandle
) : ContainerProcess {
    override fun start(): Unit = extension.init()
}