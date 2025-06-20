package dev.extframework.boot.test.test

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.maven.MavenResolverProvider
import dev.extframework.boot.test.dump
import kotlinx.coroutines.runBlocking
import java.util.logging.Logger.getLogger
import kotlin.io.path.Path
import kotlin.test.Test

class TestArchiveGraphDump {
    @Test
    fun `Test dump prints correctly`() {

        val request = SimpleMavenArtifactRequest(
            "dev.extframework:minecraft-bootstrapper:2.0.12-SNAPSHOT",
            includeScopes = setOf("compile", "runtime", "import")
        )
        val maven = MavenResolverProvider()

        val archiveGraph = ArchiveGraph.from(Path("").toAbsolutePath().parent.resolve("test-run").normalize())

        runBlocking {
            archiveGraph.get(request.descriptor, maven.resolver)

//            archiveGraph.dump(getLogger("test"))
        }
    }
}