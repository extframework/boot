package net.yakclient.boot

import com.durganmcbroom.artifact.resolver.CheckedResource
import com.durganmcbroom.artifact.resolver.open
import net.yakclient.common.util.resource.SafeResource
import java.io.InputStream
import java.net.URI

public fun CheckedResource.toSafeResource() : SafeResource = object : SafeResource {
    override val uri: URI
        get() = URI.create(this@toSafeResource.location)

    override fun open(): InputStream = this@toSafeResource.open()
}