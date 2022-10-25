package net.yakclient.boot.event

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.BootApplication
import net.yakclient.boot.plugin.BootPlugin
import net.yakclient.boot.plugin.PluginNode

// Called after the application is loaded
public data class ApplicationLoadEvent(
    val reference: ArchiveReference,
    val handle: ArchiveHandle,
): BootEvent

// Called before the start call on the app is called
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