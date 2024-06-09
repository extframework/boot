package dev.extframework.boot.store

import java.util.concurrent.ConcurrentHashMap

public class CachingDataStore<in K, V>(
    access: DataAccess<K, V>
) : DelegatingDataStore<K, V>(access) {
    private val cache : MutableMap<K, V> = ConcurrentHashMap()

    override fun get(key: K): V?  =
        cache[key] ?: super.get(key)

    override fun put(key: K, value: V) {
        super.put(key, value)
        cache[key] = value
    }
}
