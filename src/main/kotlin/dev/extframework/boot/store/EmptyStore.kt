package dev.extframework.boot.store

public class EmptyStore<T, K> : DataStore<T, K> {
    override val access: DataAccess<T, K>
        get() = throw UnsupportedOperationException()
}