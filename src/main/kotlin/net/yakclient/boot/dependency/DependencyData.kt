package net.yakclient.boot.dependency

import net.yakclient.boot.DescriptorKey
import net.yakclient.boot.archive.ArchiveData
import net.yakclient.common.util.resource.SafeResource

public data class DependencyData(
    override val key: DescriptorKey,
    override val archive: SafeResource?,
    override val children: List<DescriptorKey>,
) : ArchiveData