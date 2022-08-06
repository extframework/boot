package net.yakclient.boot.archive

public abstract class ArchiveGraph<T: ArchiveNode>(
    public val store: ArchiveStore
) {
    public abstract val graph: Map<ArchiveDescriptor, T>

    public abstract class GraphPopulator(
//        public val loader: ArchiveLoader<I>,
//        public val postLoader: ArchivePostLoader<I, T>,
    ) {
        public abstract fun load(name: String) : T?
    }
}