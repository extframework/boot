package dev.extframework.boot.dependency

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import java.nio.file.Path


public data class CachedArtifact(
    val path: Path?,
    val transitives: List<CachedDescriptor>,
    val desc: CachedDescriptor
) {
    public data class CachedDescriptor(
        override val name: String,
    ) : ArtifactMetadata.Descriptor
}