package net.yakclient.boot.loader

import net.yakclient.boot.util.toEnumeration
import java.net.URL
import java.nio.ByteBuffer
import java.security.ProtectionDomain
import java.util.*

public open class IntegratedLoader(
    name: String,
    protected val classProvider: ClassProvider = object : ClassProvider {
        override val packages: Set<String> = HashSet()

        override fun findClass(name: String): Class<*>? = null
    },
    protected val sourceProvider: SourceProvider = object : SourceProvider {
        override val packages: Set<String> = HashSet()

        override fun findSource(name: String): ByteBuffer? = null
    },
    protected val resourceProvider: ResourceProvider = object : ResourceProvider {
        override fun findResources(name: String): Sequence<URL> = emptySequence()
    },
    protected val sourceDefiner: SourceDefiner = SourceDefiner { n, b, cl, d ->
        d(n, b, ProtectionDomain(null, null, cl, null))
    },
    parent: ClassLoader,
) : ClassLoader(name, parent) {
    override fun findClass(name: String): Class<*> =
        classProvider.findClass(name) ?: throw ClassNotFoundException(name)

    override fun findResources(name: String): Enumeration<URL> {
        return resourceProvider.findResources(name).toEnumeration()
    }

    override fun findResource(name: String): URL? =
        resourceProvider.findResources(name).firstOrNull()

//    override fun findResource(mn: String?, name: String): URL? =
//        if (mn == null) sourceProvider.getResource(name) else sourceProvider.getResource(name, mn)

    override fun loadClass(name: String): Class<*> = synchronized(getClassLoadingLock(name)) {
        findLoadedClass(name)
            ?: runCatching {  parent.loadClass(name) }.getOrNull()
            ?: tryDefine(name)
            ?: classProvider.findClass(name)
            ?: throw ClassNotFoundException(name)
    }

    protected open fun tryDefine(name: String): Class<*>? = sourceProvider.findSource(name)?.let {
        sourceDefiner.define(
            name,
            it, this, ::defineClass
        )
    }

    override fun toString(): String {
        return name
    }

    private companion object {
        init {
            registerAsParallelCapable()
        }
    }
}