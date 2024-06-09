package dev.extframework.`object`

public interface ObjectContainer<T> {
    public fun get(name: String): T?

    // False if the type was not added due to a collision
    public fun has(name: String): Boolean

    public fun objects() : Map<String, T>
}

public interface MutableObjectContainer<T> : ObjectContainer<T> {
    public fun register(name: String, obj: T): Boolean
}