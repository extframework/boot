package net.yakclient.boot.component

import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.common.util.LazyMap
import net.yakclient.common.util.ServiceMapCollector

public class ComponentFactoryProvider(
        private val softwareComponentGraph: SoftwareComponentGraph,
        private val delegate: MutableMap<SoftwareComponentDescriptor, ComponentFactory<*, *>> = HashMap()
) : Map<SoftwareComponentDescriptor, ComponentFactory<*, *>> by HashMap() {
    override fun get(key: SoftwareComponentDescriptor): ComponentFactory<*, *>? {
        if (delegate.contains(key)) return delegate[key]
       val lazyVal = softwareComponentGraph.get(key).tapLeft{ throw it }.orNull()!!.factory
        if (lazyVal != null) delegate[key] = lazyVal
        return lazyVal
    }

    public fun get(key: String) : ComponentFactory<*, *> ? {
        val descriptor = SoftwareComponentDescriptor.parseDescription(key) ?: throw IllegalArgumentException("Invalid descriptor: '$key'")

        return get(descriptor)
    }
}