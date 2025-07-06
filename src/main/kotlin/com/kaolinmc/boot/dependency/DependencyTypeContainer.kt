package com.kaolinmc.boot.dependency

import com.kaolinmc.`object`.ObjectContainer

public typealias DependencyTypeContainer = ObjectContainer<DependencyResolverProvider<*, *, *>>

//public open class DependencyTypeContainer (
//    public val archiveGraph: ArchiveGraph
//): ObjectContainerImpl<DependencyResolverProvider<*, *, *>>() {
//    override fun register(name: String, obj: DependencyResolverProvider<*, *, *>): Boolean {
//        archiveGraph.registerResolver(obj.resolver)
//        return super.register(name, obj)
//    }
//}