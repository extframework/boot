package net.yakclient.boot.loader

import net.yakclient.archives.ArchiveReference
import net.yakclient.common.util.readInputStream
import java.net.URL
import java.nio.ByteBuffer

public class ArchiveSourceProvider(
    private val archive: ArchiveReference
) : SourceProvider {
    override val packages: Set<String> = archive.reader.entries()
        .map(ArchiveReference.Entry::name)
        .filter { it.endsWith(".class") }
        .filterNot { it == "module-info.class" }
        .mapTo(HashSet()) { it.removeSuffix(".class").replace('/', '.').packageFormat }

    override fun getSource(name: String): ByteBuffer? =
        archive.reader[name.dotClassFormat]?.resource?.open()?.readInputStream()?.let(ByteBuffer::wrap)

    override fun getResource(name: String): URL? = archive.reader[name]?.resource?.uri?.toURL()

    override fun getResource(name: String, module: String): URL? = getResource(name)
}