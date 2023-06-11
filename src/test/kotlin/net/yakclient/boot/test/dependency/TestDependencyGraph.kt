package net.yakclient.boot.test.dependency

import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import net.yakclient.boot.main.createMavenDependencyGraph
import java.nio.file.Files
import kotlin.test.Test

class TestDependencyGraph {
    @Test
    fun `Test maven basic dependency loading`() {
        val basePath = Files.createTempDirectory("m2cache")

        val graph = createMavenDependencyGraph(basePath)

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