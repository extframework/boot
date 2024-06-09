package dev.extframework.boot.loader

import dev.extframework.boot.util.packageName
import java.net.URL
import java.nio.ByteBuffer

public class DelegatingClassProvider(
    delegates: List<ClassProvider>
) : ClassProvider {
    private fun <V, K> Iterable<V>.flatGroupBy(transformer: (V) -> Iterable<K>): Map<K, List<V>> =
        flatMap { v -> transformer(v).map { it to v } }.groupBy { it.first }
            .mapValues { p -> p.value.map { it.second } }

    override val packages: Set<String> = delegates.flatMapTo(HashSet(), ClassProvider::packages)
    private val packageMap: Map<String, List<ClassProvider>> =
        delegates.flatGroupBy { it.packages }

    override fun findClass(name: String): Class<*>? = packageMap[name.packageName]?.firstNotNullOfOrNull { it.findClass(name) }

//    override fun findClass(name: String, module: String): Class<*>? = findClass(name)
}

public class DelegatingSourceProvider(
    delegates: List<SourceProvider>,
) : SourceProvider {
    private fun <V, K> Iterable<V>.flatGroupBy(transformer: (V) -> Iterable<K>): Map<K, List<V>> =
        flatMap { v -> transformer(v).map { it to v } }.groupBy { it.first }
            .mapValues { p -> p.value.map { it.second } }

    override val packages: Set<String> = delegates.flatMapTo(HashSet(), SourceProvider::packages)
    private val packageMap: Map<String, List<SourceProvider>> = delegates.flatGroupBy { it.packages }

    override fun findSource(name: String): ByteBuffer? =
        packageMap[name.packageName]?.firstNotNullOfOrNull { it.findSource(name) }

}

public class DelegatingResourceProvider(
    private val delegates: List<ResourceProvider>
) : ResourceProvider {
    override fun findResources(name: String): Sequence<URL> {
        return delegates.asSequence().flatMap { it.findResources(name) }
    }

}