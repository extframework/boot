package net.yakclient.boot.loader

import java.net.URL
import java.nio.ByteBuffer
import java.security.ProtectionDomain
import java.util.concurrent.CopyOnWriteArrayList

public open class MutableSourceProvider(
    protected val delegateSources: MutableList<SourceProvider>,
) : SourceProvider {
    private fun <V, K> Iterable<V>.flatGroupBy(transformer: (V) -> Iterable<K>): Map<K, List<V>> =
        flatMap { v -> transformer(v).map { it to v } }.groupBy { it.first }
            .mapValues { p -> p.value.map { it.second } }

    override val packages: Set<String>
        get() = delegateSources.flatMapTo(HashSet(), SourceProvider::packages)
    protected val packageMap: Map<String, List<SourceProvider>>
        get() = delegateSources.flatGroupBy { it.packages }

    override fun findSource(name: String): ByteBuffer? =
        packageMap[name.substring(0, name.lastIndexOf('.')
            .let { if (it == -1) 0 else it })]?.firstNotNullOfOrNull { it.findSource(name) }

    public fun add(provider: SourceProvider) {
        delegateSources.add(provider)
    }
}

public open class MutableClassProvider(
    protected val delegateClasses: MutableList<ClassProvider>,
) : ClassProvider {
    private fun <V, K> Iterable<V>.flatGroupBy(transformer: (V) -> Iterable<K>): Map<K, List<V>> =
        flatMap { v -> transformer(v).map { it to v } }.groupBy { it.first }
            .mapValues { p -> p.value.map { it.second } }

    override val packages: Set<String>
        get() = delegateClasses.flatMapTo(HashSet(), ClassProvider::packages)
    protected val packageMap: Map<String, List<ClassProvider>>
        get() = delegateClasses.flatGroupBy { it.packages }
    override fun findClass(name: String): Class<*>? {
        return packageMap[name.substring(0, name.lastIndexOf('.')
            .let { if (it == -1) 0 else it })]?.firstNotNullOfOrNull { it.findClass(name) }
    }

    public fun add(provider: ClassProvider) {
        delegateClasses.add(provider)
    }
}

public open class MutableResourceProvider(
    protected val delegateResources: MutableList<ResourceProvider>
) : ResourceProvider {
    override fun findResources(name: String): Sequence<URL> {
        return delegateResources.asSequence().flatMap { it.findResources(name) }
    }

    public fun add(provider: ResourceProvider) {
        delegateResources.add(provider)
    }
}

public open class MutableClassLoader(
    name: String,
    private val sources: MutableSourceProvider = MutableSourceProvider(CopyOnWriteArrayList()),
    private val classes: MutableClassProvider = MutableClassProvider(CopyOnWriteArrayList()),
    private val resources: MutableResourceProvider = MutableResourceProvider(CopyOnWriteArrayList()),
    sd : SourceDefiner = SourceDefiner { n, b, cl, d ->
        d(n, b, ProtectionDomain(null, null, cl, null))
    },
    parent: ClassLoader
) : IntegratedLoader(name, sourceProvider = sources, classProvider = classes, resourceProvider = resources, sourceDefiner = sd, parent = parent) {
    public fun addSources(provider: SourceProvider) {
        sources.add(provider)
    }

    public fun addClasses(provider: ClassProvider) {
        classes.add(provider)
    }

    public fun addResources(provider: ResourceProvider) {
        resources.add(provider)
    }

    private companion object {
        init {
            registerAsParallelCapable()
        }
    }
}