package net.yakclient.boot.container

import net.yakclient.boot.archive.ArchiveException
import net.yakclient.boot.container.volume.ContainerVolume

//public object ContainerLoader {
//    public fun  createHandle(): ContainerHandle = ContainerHandle()
//
//    public suspend fun <T : ArchiveContainerInfo> load(
//        info: T,
//        handle: ContainerHandle,
//        loader: ContainerArchiveLoader<T>,
//        volume: ContainerVolume,
//        privilegeManager: PrivilegeManager,
//    ): JobResult<ArchiveContainer, ArchiveException> = jobScope {
//        val container = ArchiveContainer(loader.load(info).bind(), volume, privilegeManager)
//        handle.handle = container
//
//        container
//    }
//}