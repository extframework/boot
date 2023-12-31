package net.yakclient.boot.loader

import java.net.URL
import java.nio.ByteBuffer
import java.security.ProtectionDomain

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

    override fun getSource(name: String): ByteBuffer? =
        packageMap[name.substring(0, name.lastIndexOf('.')
            .let { if (it == -1) 0 else it })]?.firstNotNullOfOrNull { it.getSource(name) }

    override fun getResource(name: String): URL? =
        delegateSources.firstNotNullOfOrNull { it.getResource(name) }

    override fun getResource(name: String, module: String): URL? =
        delegateSources.firstNotNullOfOrNull { it.getResource(name, module) }

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

    override fun findClass(name: String, module: String): Class<*>? {
        return findClass(name)
    }

    public fun add(provider: ClassProvider) {
        delegateClasses.add(provider)
    }
}

public open class MutableClassLoader(
    private val sources: MutableSourceProvider,
    private val classes: MutableClassProvider,
    sd : SourceDefiner = SourceDefiner { n, b, cl, d ->
        d(n, b, ProtectionDomain(null, null, cl, null))
    },
    parent: ClassLoader
) : IntegratedLoader(sp = sources, cp = classes, sd = sd, parent = parent) {
    public fun addSource(provider: SourceProvider) {
        sources.add(provider)
    }

    public fun addClasses(provider: ClassProvider) {
        classes.add(provider)
    }
}