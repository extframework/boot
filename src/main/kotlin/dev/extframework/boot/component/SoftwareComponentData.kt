package dev.extframework.boot.component

import dev.extframework.boot.component.artifact.SoftwareComponentDescriptor
import java.nio.file.Path

public data class SoftwareComponentData(
    val key: SoftwareComponentDescriptor,
    val archive: Path?,
    val children: List<SoftwareComponentDescriptor>,
    val dependencies: List<SoftwareComponentDependencyData>,
    val runtimeModel: SoftwareComponentModel,
)

public data class SoftwareComponentDependencyData(
    val type: String,
    val request: Map<String, String>,
)