package net.yakclient.boot.dependency

import net.yakclient.`object`.ObjectContainerImpl

//public class DependencyTypeProvider : ServiceMapCollector<String, DependencyGraphProvider<*, *, *>>({
//    it.name
//}) {
//    public fun getByType(name: String): DependencyGraphProvider<*, *, *>? = services[name]
//}

public class DependencyTypeContainer : ObjectContainerImpl<DependencyGraphProvider<*, *, *>>()