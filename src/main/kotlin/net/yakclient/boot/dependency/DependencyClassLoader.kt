package net.yakclient.boot.dependency

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.loader.ArchiveClassProvider
import net.yakclient.boot.loader.ArchiveSourceProvider
import net.yakclient.boot.loader.DelegatingClassProvider
import net.yakclient.boot.loader.IntegratedLoader
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.security.SecureSourceDefiner

public fun DependencyClassLoader(ref: ArchiveReference, children: Set<ArchiveHandle>, parent: PrivilegeManager): ClassLoader = IntegratedLoader(
    cp = DelegatingClassProvider(children.map(::ArchiveClassProvider)),
    sp = ArchiveSourceProvider(ref),
    sd = SecureSourceDefiner(PrivilegeManager(parent, parent.privileges) {
        it.requester.parent?.request(it.privilege)
    }),
    parent = ClassLoader.getSystemClassLoader()
)
