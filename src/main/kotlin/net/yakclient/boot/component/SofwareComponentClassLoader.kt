package net.yakclient.boot.component

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.loader.ArchiveSourceProvider
import net.yakclient.boot.loader.DelegatingClassProvider
import net.yakclient.boot.loader.IntegratedLoader

//public fun SofwareComponentClassLoader(name: String, parent: ClassLoader, archive: ArchiveReference, children: Set<ArchiveHandle>): ClassLoader = IntegratedLoader(
//    name = "$name software component loader",
//    DelegatingClassProvider(children.map(::ArchiveClassProvider)),
//    ArchiveSourceProvider(archive),
//    parent = parent
//)