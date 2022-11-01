package net.yakclient.boot.component

public interface SoftwareComponent {
    public fun onEnable(context: ComponentLoadContext)

    public fun onDisable(context: ComponentUnloadContext)
}