package dev.extframework.boot.dependency

import dev.extframework.`object`.ObjectContainer

public typealias DependencyTypeContainer = ObjectContainer<DependencyResolverProvider<*, *, *>>

//public open class DependencyTypeContainer (
//    public val archiveGraph: ArchiveGraph
//): ObjectContainerImpl<DependencyResolverProvider<*, *, *>>() {
//    override fun register(name: String, obj: DependencyResolverProvider<*, *, *>): Boolean {
//        archiveGraph.registerResolver(obj.resolver)
//        return super.register(name, obj)
//    }
//}