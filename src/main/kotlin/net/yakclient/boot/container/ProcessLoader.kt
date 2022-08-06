package net.yakclient.boot.container

public interface ProcessLoader<in I: ContainerInfo, out P: ContainerProcess> {
    public fun load(info: I) : P
}