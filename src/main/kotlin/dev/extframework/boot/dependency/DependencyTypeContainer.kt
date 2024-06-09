package dev.extframework.boot.dependency

import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.`object`.ObjectContainerImpl


public class DependencyTypeContainer (
    private val archiveGraph: ArchiveGraph
): ObjectContainerImpl<DependencyResolverProvider<*, *, *>>() {
    override fun register(name: String, obj: DependencyResolverProvider<*, *, *>): Boolean {
        archiveGraph.registerResolver(obj.resolver)
        return super.register(name, obj)
    }
}