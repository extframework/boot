package net.yakclient.boot.loader

import java.net.URL
import java.nio.ByteBuffer

// Name in non-internal jvm format(Eg. java.lang.String)
public interface SourceProvider {
    public val packages: Set<String>

    public fun getSource(name: String): ByteBuffer?

    public fun getResource(name: String): URL?

    public fun getResource(name: String, module: String) : URL?
}