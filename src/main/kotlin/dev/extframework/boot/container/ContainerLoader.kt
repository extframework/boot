package dev.extframework.boot.container

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