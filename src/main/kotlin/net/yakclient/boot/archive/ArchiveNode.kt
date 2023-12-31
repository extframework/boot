package net.yakclient.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import net.yakclient.archives.ArchiveHandle

public interface ArchiveNode<T: ArchiveNode<T>> {
    public val descriptor: ArtifactMetadata.Descriptor
    public val archive: ArchiveHandle?
    public val children: Set<T>
}

public fun ArchiveNode<*>.handleOrChildren(): Set<ArchiveHandle> =
    archive?.let(::setOf) ?: children.flatMapTo(HashSet()) { it.handleOrChildren() }