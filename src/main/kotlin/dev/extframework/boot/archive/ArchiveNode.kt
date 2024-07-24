package dev.extframework.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import dev.extframework.archives.ArchiveHandle

public interface ArchiveNode<T: ArtifactMetadata.Descriptor> : IArchive<T> {
    override val descriptor: T
    public val access: ArchiveAccessTree
}

public interface ClassLoadedArchiveNode<T: ArtifactMetadata.Descriptor> : ArchiveNode<T> {
    public val handle: ArchiveHandle
}