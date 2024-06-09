package dev.extframework.boot.store

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

public open class DelegatingDataStore<in K, V>(override val access: DataAccess<K, V>) : DataStore<K, V> {
    private val lock = ReentrantLock()

    override fun get(key: K): V? = lock.withLock {
        return super.get(key)
    }

    override fun put(key: K, value: V): Unit = lock.withLock {
        super.put(key, value)
    }

    override fun contains(key: K): Boolean = lock.withLock {
        return super.contains(key)
    }
}