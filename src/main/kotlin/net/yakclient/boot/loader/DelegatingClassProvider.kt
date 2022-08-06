package net.yakclient.boot.loader

public class DelegatingClassProvider(
    private val delegates: List<ClassProvider>
) : ClassProvider {
    private fun <V, K> Iterable<V>.flatGroupBy(transformer: (V) -> Iterable<K>): Map<K, List<V>> =
        flatMap { v -> transformer(v).map { it to v } }.groupBy { it.first }
            .mapValues { p -> p.value.map { it.second } }

    override val packages: Set<String> = delegates.flatMapTo(HashSet(), ClassProvider::packages)
    private val packageMap: Map<String, List<ClassProvider>> =
        delegates.flatGroupBy { it.packages }
    
    override fun findClass(name: String): Class<*>? = packageMap[name.packageFormat]?.firstNotNullOfOrNull { it.findClass(name) }

    override fun findClass(name: String, module: String): Class<*>? = findClass(name)
}