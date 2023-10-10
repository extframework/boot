package net.yakclient.boot.test.dependency

import bootFactories
import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import kotlinx.coroutines.runBlocking
import net.yakclient.boot.main.createMavenDependencyGraph
import orThrow
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

        val node = runBlocking(bootFactories()) {
            loader.cache(
                SimpleMavenArtifactRequest(
                    "org.jetbrains.kotlin:kotlin-stdlib:1.6.10",
                    includeScopes = setOf("compile", "runtime", "import")
                )
            )
        }
        println(node.orThrow())
    }
}