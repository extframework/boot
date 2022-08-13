package net.yakclient.boot.test.extension.yak

import net.yakclient.boot.DescriptorKey
import net.yakclient.boot.extension.yak.YakErm
import net.yakclient.boot.extension.yak.YakExtensionData
import net.yakclient.boot.extension.yak.YakExtensionDataAccess
import net.yakclient.boot.extension.yak.artifact.YakExtDescriptor
import net.yakclient.common.util.resolve
import net.yakclient.common.util.resource.ExternalResource
import net.yakclient.common.util.resource.LocalResource
import java.net.URI
import java.nio.file.Path
import java.util.HexFormat
import kotlin.test.Test

class TestYakExtensionDataAccess {
    private val access = YakExtensionDataAccess(
        Path.of(System.getProperty("user.dir")) resolve "cache/ext",
    )

    @Test
    fun `Test extension data access write`() {
        val desc = YakExtDescriptor(
            "net.yakclient",
            "sample",
            "1"
        )
        val key = DescriptorKey(desc)

        val resource = ExternalResource(URI.create("https://pastebin.com/raw/wwvdjvEj"), HexFormat.of().parseHex("ee290b0bda19f1541ab98915c0f33357eb85497d"), "SHA1")
        val erm = YakErm(
            "net.yakclient",
            "sample",
            "1",
            "jar",
            "org.sample.DoesNotExist",
            null,
            listOf(),
            listOf(),
            listOf(),
            listOf()
        )

        val data = YakExtensionData(
            key,
            listOf(),
            resource,
            erm
        )

        access.write(key, data)

        checkNotNull(access.read(key))

        println(String(resource.open().readAllBytes()))
    }

    // I dont really need this test, but i WANT it...
    @Test
    fun `Test extension data access read`() {
        `Test extension data access write`()

        val desc = YakExtDescriptor(
            "net.yakclient",
            "sample",
            "1"
        )
        val key = DescriptorKey(desc)

        checkNotNull(access.read(key))
    }
}