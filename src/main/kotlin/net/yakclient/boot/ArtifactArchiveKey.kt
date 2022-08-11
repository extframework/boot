package net.yakclient.boot

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import net.yakclient.boot.archive.ArchiveKey

public data class ArtifactArchiveKey(
    public val desc: ArtifactMetadata.Descriptor
) : ArchiveKey