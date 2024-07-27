package dev.extframework.boot.loader

import com.durganmcbroom.resources.openStream
import dev.extframework.archives.ArchiveReference
import dev.extframework.boot.util.dotClassFormat
import dev.extframework.boot.util.packageName
import dev.extframework.common.util.readInputStream
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer

public interface SourceProvider {
    public val packages: Set<String>

    public fun findSource(name: String): ByteBuffer?
}

public val ArchiveReference.packages: Set<String>
    get() = reader.entries()
        .map(ArchiveReference.Entry::name)
        .filter { it.endsWith(".class") }
        .filterNot { it == "module-info.class" }
        .mapTo(HashSet()) { it.removeSuffix(".class").replace('/', '.').packageName }

public open class ArchiveSourceProvider(
    protected val archive: ArchiveReference
) : SourceProvider {
    override val packages: Set<String> = archive.packages

    override fun findSource(name: String): ByteBuffer? =
        runBlocking {
            archive.reader[name.dotClassFormat]
                ?.resource
                ?.openStream()
                ?.readInputStream()
                ?.let(ByteBuffer::wrap)
        }
}