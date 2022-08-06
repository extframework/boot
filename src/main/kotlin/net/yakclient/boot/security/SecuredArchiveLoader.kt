package net.yakclient.boot.security

import net.yakclient.boot.archive.ArchiveLoader

public class SecuredArchiveLoader : ArchiveLoader<> {
    public data class PostLoadedSecuredArchive(
        val manager: PrivilegeManager
    )
}