package net.yakclient.boot.dependency

import net.yakclient.`object`.MutableObjectContainer

//public class DependencyTypeProvider : ServiceMapCollector<String, DependencyGraphProvider<*, *, *>>({
//    it.name
//}) {
//    public fun getByType(name: String): DependencyGraphProvider<*, *, *>? = services[name]
//}

public typealias DependencyTypeContainer = MutableObjectContainer<DependencyGraphProvider<*, *, *>>