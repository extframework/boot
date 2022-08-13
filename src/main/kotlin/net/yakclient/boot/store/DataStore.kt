package net.yakclient.boot.store

public interface DataStore<K: PersistenceKey, V: Persisted> {
    public val access: DataAccess<K, V>

    public operator fun get(key: K) : V? = access.read(key)

    public fun put(key: K, value: V): Unit = access.write(key, value)

    public fun contains(key: K) : Boolean = access.contains(key)
}