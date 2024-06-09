package dev.extframework.boot.store

public interface DataAccess<in K , V> {
    public fun read(key: K) : V?

    public fun write(key: K, value: V)

    public fun contains(key: K) : Boolean = read(key) != null
}
