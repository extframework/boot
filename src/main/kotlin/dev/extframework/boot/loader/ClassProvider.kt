package dev.extframework.boot.loader

import dev.extframework.archives.ArchiveHandle
import dev.extframework.boot.util.packageName

public interface ClassProvider {
    public val packages: Set<String>

    public fun findClass(name: String): Class<*>?
}

public open class ArchiveClassProvider(
    protected val archive: ArchiveHandle
) : ClassProvider {
    override val packages: Set<String> by archive::packages

    override fun findClass(name: String): Class<*>? =
        if (packages.contains(name.packageName)) dev.extframework.common.util.runCatching(ClassNotFoundException::class) {
            archive.classloader.loadClass(
                name
            )
        } else null
}

public fun emptyClassProvider() : ClassProvider {
    return object : ClassProvider {
        override val packages: Set<String> = setOf()

        override fun findClass(name: String): Class<*>? {
            return null
        }
    }
}

public fun ArchiveClassProvider(archive: ArchiveHandle?) : ClassProvider = if (archive == null) emptyClassProvider() else ArchiveClassProvider(archive)