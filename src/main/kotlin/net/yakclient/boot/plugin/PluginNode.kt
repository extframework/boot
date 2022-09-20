package net.yakclient.boot.plugin

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.archive.ArchiveNode
import net.yakclient.boot.dependency.DependencyNode

public data class PluginNode(
    override val archive: ArchiveHandle?,
    override val children: Set<PluginNode>,
    val dependencies: Set<DependencyNode>,
    val runtimeModel: PluginRuntimeModel,
    val plugin: BootPlugin?
) : ArchiveNode