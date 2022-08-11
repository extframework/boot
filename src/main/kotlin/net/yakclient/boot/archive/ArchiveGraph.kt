package net.yakclient.boot.archive

import net.yakclient.boot.store.DataStore

public abstract class ArchiveGraph<N: ArchiveNode, K: ArchiveKey, V: ArchiveData>(
    public val store: DataStore<K, V>
) {
    public abstract val graph: Map<K, N>

    public abstract inner class GraphPopulator {
        public abstract fun load(name: String) : N?
    }
}