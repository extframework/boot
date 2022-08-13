package net.yakclient.boot.extension.yak

import net.yakclient.boot.DescriptorKey
import net.yakclient.boot.archive.ArchiveKey
import net.yakclient.boot.extension.ExtensionData
import net.yakclient.common.util.resource.SafeResource

public data class YakExtensionData(
    override val key: DescriptorKey,
    override val children: List<DescriptorKey>,
    override val archive: SafeResource?,
    override val dependencies: List<ArchiveKey>,
    val erm: YakErm,
) : ExtensionData
