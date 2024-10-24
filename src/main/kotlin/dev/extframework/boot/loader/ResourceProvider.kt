package dev.extframework.boot.loader

import dev.extframework.archives.ArchiveHandle
import dev.extframework.archives.ArchiveReference
import java.net.URL

public interface ResourceProvider {
    public fun findResources(name: String): Sequence<URL>
}

public open class ArchiveResourceProvider private constructor(
    protected val resourceProvider: (String) -> URL?
) : ResourceProvider {
    public constructor(reference: ArchiveReference) : this({
        reference.reader[it]?.resource?.location?.let(::URL)
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