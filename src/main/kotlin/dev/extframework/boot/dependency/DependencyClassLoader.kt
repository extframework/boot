package dev.extframework.boot.dependency

import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import dev.extframework.boot.loader.ArchiveClassProvider
import dev.extframework.boot.loader.ArchiveSourceProvider
import dev.extframework.boot.loader.DelegatingClassProvider
import dev.extframework.boot.loader.IntegratedLoader

public fun DependencyClassLoader(ref: ArchiveReference, children: Set<ArchiveHandle>): ClassLoader = IntegratedLoader(
    name = ref.name?.let { "$it loader" } ?: ref.location.path.substringAfterLast("/").substringBeforeLast("-"),
    classProvider = DelegatingClassProvider(children.map(::ArchiveClassProvider)),
    sourceProvider = ArchiveSourceProvider(ref),
//    sourceDefiner = SecureSourceDefiner(PrivilegeManager(parent, parent.privileges) {
//        it.requester.parent?.request(it.privilege)
//    }, ref.location),
    parent = ClassLoader.getSystemClassLoader()
)
