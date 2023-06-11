package net.yakclient.boot.component.context

public interface ContextNodeTree {
    public operator fun get(name: String) : ContextNodeValue?

    public fun keys() : Set<String>
}