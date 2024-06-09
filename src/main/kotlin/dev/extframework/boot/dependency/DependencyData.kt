package dev.extframework.boot.dependency

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import java.nio.file.Path

public data class DependencyData<T: ArtifactMetadata.Descriptor>(
    val key: T,
    val archive: Path?,
    val children: List<T>,
)