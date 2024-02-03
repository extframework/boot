package net.yakclient.boot.loader

import net.yakclient.archives.ArchiveReference
import net.yakclient.boot.util.dotClassFormat
import net.yakclient.boot.util.packageName
import net.yakclient.common.util.readInputStream
import java.nio.ByteBuffer

public interface SourceProvider {
    public val packages: Set<String>

    public fun findSource(name: String): ByteBuffer?
}

public val ArchiveReference.packages : Set<String>
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
        archive.reader[name.dotClassFormat]?.resource?.open()?.readInputStream()?.let(ByteBuffer::wrap)
}