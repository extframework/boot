package net.yakclient.boot.archive

import net.yakclient.boot.store.Persisted
import net.yakclient.common.util.resource.SafeResource
import java.nio.file.Path

public interface ArchiveData : Persisted {
    public val archive: SafeResource?
    public val children: List<ArchiveKey>
}
