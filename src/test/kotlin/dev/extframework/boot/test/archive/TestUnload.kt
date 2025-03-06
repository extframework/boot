package dev.extframework.boot.test.archive

import BootLoggerFactory
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.launch
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.maven.MavenResolverProvider
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import kotlin.io.path.Path
import kotlin.test.Test

class TestUnload {
    @Test
    fun `Test unloading actually removes`() {
        val maven = MavenResolverProvider()

        val archiveGraph = ArchiveGraph.from(Path("test-run").toAbsolutePath())

        val request = SimpleMavenArtifactRequest(
            "dev.extframework.minecraft:minecraft-provider-def:2.0.12-SNAPSHOT",
            includeScopes = setOf("compile", "runtime", "import")
        )

        launch(BootLoggerFactory()) {
            runBlocking(Executors.newCachedThreadPool().asCoroutineDispatcher()) {
                archiveGraph.cacheAsync(
                    request,
                    SimpleMavenRepositorySettings.default("https://maven.extframework.dev/snapshots"),
                    maven.resolver
                )().merge()
                archiveGraph.getAsync(request.descriptor, maven.resolver)().merge()

                // BasicDependencyNode 5164
                val size = archiveGraph.nodes().size

                val result = archiveGraph.unload(
                    request.descriptor
                )().merge()

                check(result.size == size)
                check(archiveGraph.nodes().isEmpty())
            }
        }

        System.gc()
        println("Ending")
    }

    @Test
    fun `Test throws constrained exception`() {
        val maven = MavenResolverProvider()

        val archiveGraph = ArchiveGraph.from(Path("test-run").toAbsolutePath())

        val request = SimpleMavenArtifactRequest(
            "dev.extframework.minecraft:minecraft-provider-def:2.0.12-SNAPSHOT",
            includeScopes = setOf("compile", "runtime", "import")
        )

        launch(BootLoggerFactory()) {
            runBlocking(Executors.newCachedThreadPool().asCoroutineDispatcher()) {
                archiveGraph.cacheAsync(
                    request,
                    SimpleMavenRepositorySettings.default("https://maven.extframework.dev/snapshots"),
                    maven.resolver
                )().merge()
                archiveGraph.getAsync(request.descriptor, maven.resolver)().merge()

                val result = runCatching {
                    archiveGraph.unload(
                        SimpleMavenDescriptor.parseDescription("com.durganmcbroom:jobs-jvm:1.3.2-SNAPSHOT")!!
                    )().merge()
                }

                check(result.isFailure)
                check(result.exceptionOrNull() is ArchiveException.UnloadingConstrained)
            }
        }
    }
}