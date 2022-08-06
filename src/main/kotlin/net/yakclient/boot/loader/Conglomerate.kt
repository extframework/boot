package net.yakclient.boot.loader

import java.net.URL
import java.nio.ByteBuffer
import java.util.*
//
//public open class ClConglomerate(
//    parent: ClassLoader,
//    private val provider: ConglomerateSourceProvider,
//    components: List<ClassProvider>
//) : ProvidedClassLoader(
//    provider,
//    components,
//    parent
//), ClassProvider {
//    public constructor(parent: ClassLoader, providers: List<SourceProvider>, components: List<ClassProvider>) : this(
//        parent,
//        ConglomerateSourceProvider(providers),
//        components
//    )
//
//    override val packages: Set<String> = provider.packages
//
//    override fun findResources(name: String): Enumeration<URL> {
//        return Collections.enumeration(provider.getResources(name))
//    }
////
////    private fun <V, K> Iterable<V>.flatAssociateBy(transformer: (V) -> Iterable<K>): Map<K, V> =
////        flatMap { v -> transformer(v).map { it to v } }.associate { it }
////
////    // Very convoluted as Github pilot created it
////    private fun <V, K> Iterable<V>.flatGroupBy(transformer: (V) -> Iterable<K>): Map<K, List<V>> =
////        flatMap { v -> transformer(v).map { it to v } }.groupBy { it.first }
////            .mapValues { p -> p.value.map { it.second } }
////
////    private val packageMap: Map<String, List<ConglomerateProvider>> = providers.flatGroupBy { it.packages }
////    private val resourceMap: Map<String, ConglomerateProvider> = providers.flatAssociateBy { it.resources }
////
////    private val domain = ProtectionDomain(CodeSource(null, arrayOf<Certificate>()), null, this, null)
////
////    override fun findClass(name: String): Class<*>? =
////        loadLocalClass(name) ?: super.findClass(name)
////
////    override fun findClass(moduleName: String?, name: String): Class<*>? = findClass(name)
////
////    override fun loadClass(name: String, resolve: Boolean): Class<*> {
////        return (loadLocalClass(name) ?: super.loadClass(name, false))?.also { if (resolve) resolveClass(it) }
////            ?: throw ClassNotFoundException(name)
////    }
////
////    private fun loadLocalClass(name: String): Class<*>? {
////        findLoadedClass(name)?.let { return it }
////
////        val bb: ByteBuffer = packageMap[name.packageFormat]
////            ?.firstNotNullOfOrNull { it.provideClass(name) }
////            ?: return null
////
////        return defineClass(name, bb, domain)
////    }
////
////    override fun findResources(name: String): Enumeration<URL> {
////        return Collections.enumeration(providers.mapNotNull { it.provideResource(name)?.toURL() })
////    }
////
////    override fun findResource(name: String): URL? =
////        resourceMap[name]?.provideResource(name)?.toURL()
////
////    override fun findResource(mn: String, name: String): URL? = findResource(name)
//}
//
//public class ConglomerateSourceProvider(
//    private val others: List<SourceProvider>
//) : SourceProvider {
//    override val packages: Set<String> = others.flatMapTo(HashSet(), SourceProvider::packages)
//
//    override fun getSource(name: String): ByteBuffer? = others.firstNotNullOfOrNull { it.getSource(name) }
//
//    override fun getResource(name: String): URL? = others.firstNotNullOfOrNull { it.getResource(name) }
//
//    internal fun getResources(name: String) : List<URL> = others.mapNotNull { it.getResource(name) }
//}
////
////public interface ConglomerateProvider {
////    public val packages: Set<String>
////    public val resources: Set<String>
////
////    public fun provideClass(name: String): ByteBuffer?
////
////    public fun provideResource(name: String): URI?
////}
////
////public open class ArchiveConglomerateProvider(
////    private val archive: ArchiveHandle
////) : ConglomerateProvider {
////    override val packages: Set<String> = archive.reader.entries()
////        .map(ArchiveHandle.Entry::name)
////        .filter { it.endsWith(".class") }
////        .filterNot { it == "module-info.class" }
////        .mapTo(HashSet()) { it.removeSuffix(".class").replace('/', '.').packageFormat }
////    override val resources: Set<String> = archive.reader.entries()
////        .map { it.name }
////        .filterNotTo(HashSet()) { it.endsWith(".class") }
////
////    override fun provideClass(name: String): ByteBuffer? =
////        archive.reader[name.dotClassFormat]?.resource?.open()?.readInputStream()?.let(ByteBuffer::wrap)// ByteBuffer.wrap()
////
////    override fun provideResource(name: String): URI? = archive.reader[name]?.resource?.uri
////}