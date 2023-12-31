package net.yakclient.boot.dependency

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.archive.ArchiveNode

public data class DependencyNode(
    override val archive: ArchiveHandle?,
    override val children: Set<DependencyNode>,
    override val descriptor: ArtifactMetadata.Descriptor,
) : ArchiveNode<DependencyNode>