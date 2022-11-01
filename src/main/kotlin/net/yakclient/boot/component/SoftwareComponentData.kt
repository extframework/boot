package net.yakclient.boot.component

import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import java.nio.file.Path

public data class SoftwareComponentData(
    val key: SoftwareComponentDescriptor,
    val archive: Path?,
    val children: List<SoftwareComponentDescriptor>,
    val dependencies: List<SoftwareComponentDependencyData>,
    val runtimeModel: SoftwareComponentModel,
    val configuration: Map<String, String>
)

public data class SoftwareComponentDependencyData(
    val type: String,
    val request: Map<String, String>,
)