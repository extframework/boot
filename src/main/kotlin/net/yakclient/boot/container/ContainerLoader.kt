package net.yakclient.boot.container

import net.yakclient.boot.container.volume.ContainerVolume
import net.yakclient.boot.security.PrivilegeManager

public object ContainerLoader {
    public fun <P: ContainerProcess> createHandle(): ContainerHandle<P> = ContainerHandle()

    public fun <T : ContainerInfo, P: ContainerProcess> load(
        info: T,
        handle: ContainerHandle<P>,
        loader: ProcessLoader<T, P>,
        volume: ContainerVolume,
        privilegeManager: PrivilegeManager,
    ): Container<P> {
        val process = loader.load(info)

        val container = Container(loader.load(info), process.archive, volume, privilegeManager)
        handle.handle = container

        return container
    }
}