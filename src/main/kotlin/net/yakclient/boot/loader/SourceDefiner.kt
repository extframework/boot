package net.yakclient.boot.loader

import java.nio.ByteBuffer
import java.security.ProtectionDomain

public fun interface SourceDefiner {
    public fun define(name: String, bb: ByteBuffer, cl: ClassLoader, definer: (String, ByteBuffer, ProtectionDomain) -> Class<*>) : Class<*>
}
