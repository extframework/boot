package net.yakclient.boot.store

public open class DelegatingDataStore<K: PersistenceKey, V: Persisted>(override val access: DataAccess<K, V>) : DataStore<K, V>