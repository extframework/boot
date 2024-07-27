package dev.extframework.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.resources.Resource
import java.nio.file.Path

/**
 * Archive data representing a cached archive. Data about archive access is
 * not stored in this type. Any extra information required for later loading
 * should be serialized and stored as a persist-able resource.
 */
public data class ArchiveData<K : ArtifactMetadata.Descriptor, T : ArchiveResource> internal constructor(
    override val descriptor: K,
    val resources: Map<String, T>,
) : IArchive<K>

/**
 * A sealed type representing the current state of the resource. During
 * caching this would be [CacheableArchiveResource] as the resource is not
 * yet cached but is "cacheable". During loading this is [CachedArchiveResource]
 * because the type has been cached in the loaded stage.
 */
public sealed interface ArchiveResource

/**
 * A resource that is cacheable
 */
public class CacheableArchiveResource(
    public val resource: Resource
) : ArchiveResource

/**
 * A resource that has been cached.
 */
public data class CachedArchiveResource(
    public val path: Path
) : ArchiveResource