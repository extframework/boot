package net.yakclient.boot.loader

import net.yakclient.archives.ArchiveReference

//// TODO Currently no archive handles are being closed, the best solution i can think of is to have some soft of collection service that keeps track of all loaders and closes them when the JVM exits or if they are not being used anymore
//public open class ArchiveLoader(
//    handle: ArchiveReference,
//    components: List<ClassProvider>,
//    parent: ClassLoader
//) : ProvidedClassLoader(
//    ArchiveSourceProvider(handle.also { check(it.isOpen) { "Given reference: ${handle.location} must be open!" } }),
//    components,
//    parent,
//)

//public open class ArchiveLoader(
//    parent: ClassLoader,
//    components: List<ClComponent>,
//    private val handle: ArchiveHandle
//) : IntegratedLoader(components, parent) {
//    private val domain = ProtectionDomain(ContainerSource(), Permissions(), this, null)
//    init {
//        check(handle.isOpen) { "Given reference: ${handle.location} must be open!" }
//    }
//
//    override fun findResource(name: String): URL? {
//        return handle.reader[name]?.resource?.uri?.toURL()
//    }
//
//    override fun findClass(name: String): Class<*>? =
//        loadLocalClass(name) ?: super.findClass(name)
//
//    override fun findClass(moduleName: String?, name: String): Class<*>? = findClass(name)
//
//    override fun loadClass(name: String, resolve: Boolean): Class<*> {
//       return (loadLocalClass(name) ?: super.loadClass(name, false))?.also { if(resolve) resolveClass(it) } ?: throw ClassNotFoundException(name)
//    }
//
//    private fun loadLocalClass(name: String) : Class<*>? {
//        findLoadedClass(name)?.let { return it }
//
//        val entry: ArchiveHandle.Entry = handle.reader["${name.replace('.', '/')}.class"] ?: return null
//
//        val bb = ByteBuffer.wrap(entry.resource.open().readInputStream())
//
//        return defineClass(name, bb, domain)
//    }
//}