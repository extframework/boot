package net.yakclient.boot.loader

import java.net.URL
import java.nio.ByteBuffer
import java.security.ProtectionDomain

public open class IntegratedLoader(
    private val cp: ClassProvider = object : ClassProvider {
        override val packages: Set<String> = HashSet()

        override fun findClass(name: String): Class<*>? = null

        override fun findClass(name: String, module: String): Class<*>? = null
    },
    private val sp: SourceProvider = object : SourceProvider {
        override val packages: Set<String> = HashSet()

        override fun getSource(name: String): ByteBuffer? = null

        override fun getResource(name: String): URL? = null

        override fun getResource(name: String, module: String): URL? = null
    },
    private val sd: SourceDefiner = SourceDefiner { n, b, cl, d ->
        d(n, b, ProtectionDomain(null, null, cl, null))
    },

    parent: ClassLoader
) : ClassLoader(parent) {
    override fun findClass(name: String): Class<*>? = findClass(null, name)

    override fun findClass(moduleName: String?, name: String): Class<*>? =
        findLoadedClass(name) ?: (if (moduleName != null) cp.findClass(moduleName, name) else cp.findClass(name))

    override fun findResource(name: String): URL? = sp.getResource(name)

    override fun findResource(mn: String?, name: String): URL? =
        if (mn == null) sp.getResource(name) else sp.getResource(name, mn)

    override fun loadClass(name: String, resolve: Boolean): Class<*> = findLoadedClass(name)
        ?: tryDefine(name, resolve)
        ?: super.loadClass(name, resolve)
        ?: throw ClassNotFoundException(name)

    private fun tryDefine(name: String, resolve: Boolean): Class<*>? = sp.getSource(name)?.let {
        sd.define(
            name,
            it, this, ::defineClass
        ).also { c -> if (resolve) resolveClass(c) }
    }
}