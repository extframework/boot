package net.yakclient.boot.dependency

import net.yakclient.boot.ArtifactArchiveKey
import net.yakclient.boot.archive.ArchiveData
import net.yakclient.common.util.resource.SafeResource

public data class DependencyData(
    override val key: ArtifactArchiveKey,
    override val archive: SafeResource?,
    override val children: List<ArtifactArchiveKey>,
) : ArchiveData