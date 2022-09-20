package net.yakclient.boot.test.plugin

import net.yakclient.boot.plugin.PluginData
import net.yakclient.boot.plugin.PluginDataAccess
import net.yakclient.boot.plugin.PluginDependencyData
import net.yakclient.boot.plugin.PluginRuntimeModel
import net.yakclient.boot.plugin.artifact.PluginDescriptor
import net.yakclient.common.util.resolve
import java.nio.file.Path
import kotlin.test.Test

class PluginStoreTests {
    @Test
    fun `Test plugin data access write`() {
        val basePath = Path.of(System.getProperty("user.dir")) resolve "cache/plugin"

        val access = PluginDataAccess(basePath)

        val desc = PluginDescriptor("org.example", "example", "1.0-EXAMPLE", null)
        access.write(
            desc,
            PluginData(
                desc,
                null,
                listOf(desc, desc),
                listOf(
                    PluginDependencyData(
                        "maven",
                        mapOf(
                            "group" to "net.questcraft",
                            "artifact" to "CrossServerCommunicationAPI",
                            "version" to "never released :("
                        ),
                    )
                ),
                PluginRuntimeModel(
                    "Plugin!",
                    "A jar",
                    null,
                    listOf(),
                    listOf()
                )
            )
        )
    }

    @Test
    fun `Test plugin data access read`() {
        val basePath = Path.of(System.getProperty("user.dir")) resolve "cache/plugin"

        val access = PluginDataAccess(basePath)

        val desc = PluginDescriptor("org.example", "example", "1.0-EXAMPLE", null)

        println(access.read(desc))
    }
}