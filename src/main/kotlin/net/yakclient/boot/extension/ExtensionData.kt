package net.yakclient.boot.extension

import net.yakclient.boot.archive.ArchiveData
import net.yakclient.boot.archive.ArchiveKey
import net.yakclient.boot.store.PersistenceKey
import net.yakclient.common.util.resource.SafeResource

public interface ExtensionData : ArchiveData {
    override val key: ArchiveKey
    override val children: List<ArchiveKey>
    override val archive: SafeResource?
    public val dependencies: List<ArchiveKey>
}
