package dev.extframework.boot.loader

import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Paths

public interface ResourceProvider {
    public fun findResources(name: String): Sequence<URL>
}

public open class ArchiveResourceProvider private constructor(
    protected val resourceProvider: (String) -> URL?
) : ResourceProvider {
    public constructor(reference: ArchiveReference) : this({
        reference.reader[it]?.let {
            URL("jar:${Paths.get(reference.location)}!${File.separatorChar}$it")
        }
    })

    public constructor(handle: ArchiveHandle) : this({
        handle.classloader.getResource(it)
    })

    override fun findResources(name: String): Sequence<URL> {
        return resourceProvider(name)?.let { sequenceOf(it) } ?: sequenceOf()
    }
}

public fun emptyResourceProvider() : ResourceProvider {
    return object : ResourceProvider {
        override fun findResources(name: String): Sequence<URL> {
            return emptySequence()
        }
    }
}

public fun ArchiveResourceProvider(handle: ArchiveHandle?) : ResourceProvider = if (handle == null) emptyResourceProvider() else ArchiveResourceProvider(handle)