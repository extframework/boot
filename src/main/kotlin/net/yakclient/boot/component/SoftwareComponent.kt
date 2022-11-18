package net.yakclient.boot.component

public interface SoftwareComponent {
    public fun onEnable(context: ComponentContext)

    public fun onDisable()
}