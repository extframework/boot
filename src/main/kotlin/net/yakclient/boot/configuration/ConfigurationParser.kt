package net.yakclient.boot.configuration

public interface ConfigurationParser<in T, out C: BootConfiguration> {
    public fun parse(value: T) : C
}