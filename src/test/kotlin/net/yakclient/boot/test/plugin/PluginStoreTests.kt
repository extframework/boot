package net.yakclient.boot.test.plugin

import net.yakclient.boot.component.SoftwareComponentData
import net.yakclient.boot.component.SoftwareComponentDataAccess
import net.yakclient.boot.component.SoftwareComponentDependencyData
import net.yakclient.boot.component.SoftwareComponentModel
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.common.util.resolve
import java.nio.file.Path
import kotlin.test.Test

class PluginStoreTests {
    @Test
    fun `Test plugin data access write`() {
        val basePath = Path.of(System.getProperty("user.dir")) resolve "cache/plugin"

        val access = SoftwareComponentDataAccess(basePath)

        val desc = SoftwareComponentDescriptor("org.example", "example", "1.0-EXAMPLE", null)
        access.write(
            desc,
            SoftwareComponentData(
                desc,
                null,
                listOf(desc, desc),
                listOf(
                    SoftwareComponentDependencyData(
                        "maven",
                        mapOf(
                            "group" to "net.questcraft",
                            "artifact" to "CrossServerCommunicationAPI",
                            "version" to "never released :("
                        ),
                    )
                ),
                SoftwareComponentModel(
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

        val access = SoftwareComponentDataAccess(basePath)

        val desc = SoftwareComponentDescriptor("org.example", "example", "1.0-EXAMPLE", null)

        println(access.read(desc))
    }
}