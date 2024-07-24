package dev.extframework.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.resources.Resource
import java.nio.file.Path

public data class ArchiveData<K : ArtifactMetadata.Descriptor, T : ArchiveResource> internal constructor(
    override val descriptor: K,
    val resources: Map<String, T>,
) : IArchive<K>

public sealed interface ArchiveResource

public class CacheableArchiveResource(
    public val resource: Resource
) : ArchiveResource

public data class CachedArchiveResource(
    public val path: Path
) : ArchiveResource