package net.yakclient.boot.test.dependency

import bootFactories
import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.JobName
import kotlinx.coroutines.runBlocking
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.main.createMavenDependencyGraph
import orThrow
import java.nio.file.Files
import kotlin.test.Test

class TestDependencyGraph {
    @Test
    fun `Test maven basic dependency loading`() {
        val basePath = Files.createTempDirectory("m2cache")

        val maven = createMavenDependencyGraph()
        val archiveGraph = ArchiveGraph(basePath)

        val request = SimpleMavenArtifactRequest(
            "net.yakclient.minecraft:minecraft-provider-def:1.0-SNAPSHOT",
            includeScopes = setOf("compile", "runtime", "import")
        )


        val node = runBlocking(bootFactories() + JobName("test")) {
            archiveGraph.cache(
                request,
                SimpleMavenRepositorySettings.local(
                    preferredHash = HashType.SHA1
                ),
//                SimpleMavenRepositorySettings.mavenCentral(
//                    preferredHash = HashType.MD5
//                ),
                maven
            ).orThrow()

            archiveGraph.get(request.descriptor, maven)
        }

        println(node.orThrow())
    }
}