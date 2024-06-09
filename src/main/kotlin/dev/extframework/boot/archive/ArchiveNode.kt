package dev.extframework.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import dev.extframework.archives.ArchiveHandle

public interface ArchiveNode<T: ArchiveNode<T>> {
    public val descriptor: ArtifactMetadata.Descriptor
    public val archive: ArchiveHandle?
    public val parents: Set<T>
    public val access: ArchiveAccessTree
    public val resolver: ArchiveNodeResolver<*, *, T, *, *>
}

//public fun ArchiveNode<*>.handleOrChildren(): Set<ArchiveHandle> =
//    archive?.let(::setOf) ?: children.flatMapTo(HashSet()) { it.handleOrChildren() }