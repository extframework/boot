package net.yakclient.boot.extension

import net.yakclient.boot.archive.ArchiveData
import net.yakclient.boot.archive.ArchiveKey
import net.yakclient.boot.extension.yak.YakErm
import net.yakclient.boot.store.PersistenceKey
import net.yakclient.common.util.resource.SafeResource

public data class ExtensionData(
    override val archive: SafeResource?,
    override val children: List<ArchiveKey>,
    override val key: PersistenceKey,
    public val erm : YakErm
) : ArchiveData
