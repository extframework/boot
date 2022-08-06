package net.yakclient.boot.dependency

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import java.nio.file.Path
import kotlin.reflect.KClass

public interface CacheableMetadataProvider<D : ArtifactMetadata.Descriptor> {
    // One MUST be initialized.
    public val descriptorType: KClass<D>

    public fun transformDescriptor(d: D) : CachedArtifact.CachedDescriptor

    public fun relativePath(d: D) : Path
}