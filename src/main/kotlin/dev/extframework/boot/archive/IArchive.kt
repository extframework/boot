package dev.extframework.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactMetadata

public interface IArchive<T: ArtifactMetadata.Descriptor> {
    public val descriptor: T
}