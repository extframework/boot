package net.yakclient.boot.component

public interface ComponentInstance<T: ComponentConfiguration> {
    public fun start()

    public fun end()
}