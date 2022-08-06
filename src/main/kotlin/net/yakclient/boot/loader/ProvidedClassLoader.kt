package net.yakclient.boot.loader

import java.net.URL
import java.nio.ByteBuffer
import java.security.CodeSource
import java.security.Permissions
import java.security.ProtectionDomain
import java.security.cert.Certificate
//
//public open class ProvidedClassLoader(
//    private val provider: SourceProvider,
//    _components: List<ClassProvider>,
//    parent: ClassLoader
//) : IntegratedLoader(
//    _components,
//    parent
//) {
//    private val defaultDomain = ProtectionDomain(CodeSource(null, arrayOf<Certificate>()), Permissions(), this, null)
//
//    override fun findResource(name: String): URL? =
//        provider.getResource(name)
//
//    override fun findResource(mn: String, name: String): URL? = findResource(name)
//
//    override fun findClass(name: String): Class<*>? =
//        findLocalClass(name) ?: super.findClass(name)
//
//    override fun findClass(moduleName: String?, name: String): Class<*>? = findClass(name)
//
//    override fun loadClass(name: String, resolve: Boolean): Class<*> {
//        return (findLoadedClass(name) ?: findLocalClass(name) ?: super.loadClass(name, false))
//            ?.also { if (resolve) resolveClass(it) }
//            ?: throw ClassNotFoundException(name)
//    }
//
//    protected open fun findLocalClass(name: String): Class<*>? {
//        return findLocalClass(name, provider.getSource(name) ?: return null)
//    }
//
//    protected open fun findLocalClass(name: String, buffer: ByteBuffer): Class<*> {
//        return defineClass(name, buffer, defaultDomain)
//    }
//}

