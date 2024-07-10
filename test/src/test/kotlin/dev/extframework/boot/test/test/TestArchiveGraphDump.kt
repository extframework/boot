package dev.extframework.boot.test.test

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.JobName
import dev.extframework.boot.maven.MavenResolverProvider
import dev.extframework.boot.test.dump
import dev.extframework.boot.test.testBootInstance
import runBootBlocking
import kotlin.test.Test

class TestArchiveGraphDump {
    @Test
    fun `Test dump prints correctly`() {
        val bInstance = testBootInstance(
            mapOf()
        )
        val request = SimpleMavenArtifactRequest(
            "dev.extframework.minecraft:minecraft-provider-def:1.0-SNAPSHOT",
            includeScopes = setOf("compile", "runtime", "import")
        )
        val maven = MavenResolverProvider()

        val archiveGraph by bInstance::archiveGraph

        runBootBlocking(JobName("test")) {
            archiveGraph.cache(
                request,
                SimpleMavenRepositorySettings.default(url = "https://maven.extframework.dev/snapshots"),
                maven.resolver
            )().merge()

            archiveGraph.get(request.descriptor, maven.resolver)().merge()

            bInstance.archiveGraph.dump()().merge()
        }
    }
}