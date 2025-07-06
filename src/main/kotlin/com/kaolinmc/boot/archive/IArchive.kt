package com.kaolinmc.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactMetadata

public interface IArchive<T: ArtifactMetadata.Descriptor> {
    public val descriptor: T
}