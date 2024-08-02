package dev.extframework.boot.test.test

import BootLoggerFactory
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.launch
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.maven.MavenResolverProvider
import dev.extframework.boot.test.dump
import java.nio.file.Path
import kotlin.test.Test

class TestArchiveGraphDump {
    @Test
    fun `Test dump prints correctly`() {
        val request = SimpleMavenArtifactRequest(
            "dev.extframework.minecraft:minecraft-provider-def:1.0-SNAPSHOT",
            includeScopes = setOf("compile", "runtime", "import")
        )
        val maven = MavenResolverProvider()

        val archiveGraph = ArchiveGraph.from(Path.of("test-run"))

        launch(JobName("test") + BootLoggerFactory()) {
            archiveGraph.cacheAsync(
                request,
                SimpleMavenRepositorySettings.default(url = "https://maven.extframework.dev/snapshots"),
                maven.resolver
            )().merge()

            archiveGraph.getAsync(request.descriptor, maven.resolver)().merge()

            archiveGraph.dump()().merge()
        }
    }
}