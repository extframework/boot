package net.yakclient.boot.plugin

import com.durganmcbroom.artifact.resolver.ArtifactRequest
import net.yakclient.boot.plugin.artifact.PluginDescriptor
import java.nio.file.Path

public data class PluginData(
    val key: PluginDescriptor,
    val archive: Path?,
    val children: List<PluginDescriptor>,
    val dependencies: List<PluginDependencyData>,
    val runtimeModel: PluginRuntimeModel
)

public data class PluginDependencyData(
    val type: String,
    val request: Map<String, String>,
)