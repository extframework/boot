package net.yakclient.boot

public interface BootApplication {
    public fun newInstance(args: Array<String>) : AppInstance
}