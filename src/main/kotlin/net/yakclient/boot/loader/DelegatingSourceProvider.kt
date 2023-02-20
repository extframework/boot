package net.yakclient.boot.loader

import net.yakclient.boot.util.packageFormat
import java.net.URL
import java.nio.ByteBuffer

public class DelegatingSourceProvider(
    private val delegates: List<SourceProvider>,
) : SourceProvider {
    private fun <V, K> Iterable<V>.flatGroupBy(transformer: (V) -> Iterable<K>): Map<K, List<V>> =
        flatMap { v -> transformer(v).map { it to v } }.groupBy { it.first }
            .mapValues { p -> p.value.map { it.second } }

    override val packages: Set<String> = delegates.flatMapTo(HashSet(), SourceProvider::packages)
    private val packageMap: Map<String, List<SourceProvider>> = delegates.flatGroupBy { it.packages }

    override fun getSource(name: String): ByteBuffer? =
        packageMap[name.packageFormat]?.firstNotNullOfOrNull { it.getSource(name) }

    override fun getResource(name: String): URL? =
       delegates.firstNotNullOfOrNull { it.getResource(name) }

    override fun getResource(name: String, module: String): URL? =
        delegates.firstNotNullOfOrNull { it.getResource(name, module) }
}