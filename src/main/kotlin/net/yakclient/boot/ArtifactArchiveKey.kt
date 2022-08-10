package net.yakclient.boot

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import net.yakclient.boot.archive.ArchiveKey

public data class ArtifactArchiveDescriptor(
    public val desc: ArtifactMetadata.Descriptor
) : ArchiveKey