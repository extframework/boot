package net.yakclient.boot.store

public open class DelegatingDataStore<in K, V>(override val access: DataAccess<K, V>) : DataStore<K, V>