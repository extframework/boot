package net.yakclient.boot.event

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.BootApplication
import net.yakclient.boot.plugin.BootPlugin
import net.yakclient.boot.plugin.PluginNode

public data class ApplicationLoadEvent(
    val reference: ArchiveReference
): BootEvent

public data class ApplicationLaunchEvent(
    val handle: ArchiveHandle,
    val app: BootApplication
) : BootEvent

public data class PluginLoadEvent(
    val node: PluginNode,
    val bootPlugin: BootPlugin
) : BootEvent

public data class PluginEnableEvent(
    val plugin: BootPlugin
)

public data class PluginDisableEvent(
    val plugin: BootPlugin
)