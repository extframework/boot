package net.yakclient.boot.store

public interface DataAccess<K : PersistenceKey, V : Persisted> {
    public fun read(key: K) : V?

    public fun write(key: K, value: V)
}
