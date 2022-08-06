package net.yakclient.boot.container

import net.yakclient.archives.Archives
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
        parent: ClassLoader,
    ): Container<P> {
//        val cl = ContainerClassLoader(ArchiveSourceProvider(info.reference), privilegeManager, ContainerSource(handle), info.dependencies.map(::ArchiveClassProvider), parent)

        Archives.resolve(info.reference, Archives.Resolvers.JPM_RESOLVER, )

        val container = Container(loader.load(info), volume, privilegeManager)
        handle.handle = container

        return container
    }
}