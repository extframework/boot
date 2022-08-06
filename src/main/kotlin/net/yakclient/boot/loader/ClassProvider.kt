package net.yakclient.boot.loader

public interface ClassProvider {
    public val packages: Set<String>

    public fun findClass(name: String): Class<*>?

    public fun findClass(name: String, module: String): Class<*>?
}
