package net.yakclient.boot.loader

import java.net.URL
import java.nio.ByteBuffer
import java.security.ProtectionDomain

// TODO Parallel capable, rename cp, sp, and sd to something reasonable (and then much refactoring)
public open class IntegratedLoader(
    protected val cp: ClassProvider = object : ClassProvider {
        override val packages: Set<String> = HashSet()

        override fun findClass(name: String): Class<*>? = null

        override fun findClass(name: String, module: String): Class<*>? = null
    },
    protected val sp: SourceProvider = object : SourceProvider {
        override val packages: Set<String> = HashSet()

        override fun getSource(name: String): ByteBuffer? = null

        override fun getResource(name: String): URL? = null

        override fun getResource(name: String, module: String): URL? = null
    },
    protected val sd: SourceDefiner = SourceDefiner { n, b, cl, d ->
        d(n, b, ProtectionDomain(null, null, cl, null))
    },
    parent: ClassLoader,
) : ClassLoader(parent) {
    override fun findClass(name: String): Class<*>? = findClass(null, name)

    override fun findClass(moduleName: String?, name: String): Class<*>? =
        findLoadedClass(name) ?: (if (moduleName != null) cp.findClass(moduleName, name) else cp.findClass(name))

    override fun findResource(name: String): URL? = sp.getResource(name)

    override fun findResource(mn: String?, name: String): URL? =
        if (mn == null) sp.getResource(name) else sp.getResource(name, mn)

    override fun loadClass(name: String, resolve: Boolean): Class<*> =
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)
                ?: tryDefine(name, resolve)
                ?: super.loadClass(name, resolve)
                ?: throw ClassNotFoundException(name)
        }

    protected open fun tryDefine(name: String, resolve: Boolean): Class<*>? = sp.getSource(name)?.let {
        sd.define(
            name,
            it, this, ::defineClass
        ).also { c -> if (resolve) resolveClass(c) }
    }
}