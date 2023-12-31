package net.yakclient.boot.dependency

import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.`object`.ObjectContainerImpl


public class DependencyTypeContainer (
    private val archiveGraph: ArchiveGraph
): ObjectContainerImpl<DependencyResolverProvider<*, *, *>>() {
    override fun register(name: String, obj: DependencyResolverProvider<*, *, *>): Boolean {
        archiveGraph.registerResolver(obj.resolver)
        return super.register(name, obj)
    }
}