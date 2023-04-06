package net.yakclient.boot.test.dependency

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.*
import net.yakclient.boot.createMavenDependencyGraph
import net.yakclient.common.util.resolve
import java.nio.file.Path
import kotlin.test.Test

class TestDependencyGraph {
    @Test
    fun `Test maven basic dependency loading`() {
        val basePath = Path.of(System.getProperty("user.dir")) resolve "cache/lib"
        val graph = createMavenDependencyGraph(basePath.toString())

        val loader = graph.cacherOf(
            SimpleMavenRepositorySettings.mavenCentral(
                preferredHash = HashType.MD5
            )
        )

        val node = loader.cache(
            SimpleMavenArtifactRequest(
                "org.springframework:spring-core:5.3.22",
               includeScopes = setOf("compile", "runtime", "import")
            )
        )
        println(node)
    }
}