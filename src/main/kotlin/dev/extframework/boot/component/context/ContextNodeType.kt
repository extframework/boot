package dev.extframework.boot.component.context

public fun interface ContextNodeType<T> {
    public fun accept(any: Any) : T?
}