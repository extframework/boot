package net.yakclient.boot.dependency

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import java.nio.file.Path
import kotlin.reflect.KClass

public interface DependencyMetadataProvider<D : ArtifactMetadata.Descriptor> {
    public val descriptorType: KClass<D>

    public fun descToString(d: D) : String

    public fun stringToDesc(d: String) : D?

    public fun relativePath(d: D) : Path

    public fun jarName(d: D) : String
}