package net.yakclient.boot.container

import net.yakclient.common.util.immutableLateInit

public class ContainerHandle<T: ContainerProcess> internal constructor() {
    public var handle: Container<T> by immutableLateInit()
}
