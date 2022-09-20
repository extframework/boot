package net.yakclient.boot.dependency

import com.durganmcbroom.artifact.resolver.ArtifactRequest
import java.nio.file.Path

public data class DependencyData<T: ArtifactRequest<*>>(
    val key: T,
    val archive: Path?,
    val children: List<T>,
)