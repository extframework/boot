package net.yakclient.boot.component.context

public fun interface ContextNodeType<T> {
    public fun accept(any: Any) : T?
}