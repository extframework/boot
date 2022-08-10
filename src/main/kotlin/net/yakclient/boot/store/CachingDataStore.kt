package net.yakclient.boot.store

public class CachingDataStore<K : PersistenceKey, V : Persisted>(
    override val access: DataAccess<K, V>
) : DataStore<K, V> {
    private val cache : MutableMap<K, V> = HashMap()

    override fun get(key: K): V?  =
        cache[key] ?: access.read(key)?.also { cache[key] = it }

    override fun put(key: K, value: V) {
        cache[key] = value
        access.write(key, value)
    }
}
