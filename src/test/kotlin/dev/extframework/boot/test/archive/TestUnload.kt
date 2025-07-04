package dev.extframework.boot.test.archive

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.maven.MavenResolverProvider
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import kotlin.io.path.Path
import kotlin.test.Test

class TestUnload {
    @Test
    fun `Test unloading actually removes`() {

        val archiveGraph = ArchiveGraph.from(Path("test-run").toAbsolutePath())
        val maven = MavenResolverProvider()

        val request = SimpleMavenArtifactRequest(
            "dev.extframework.minecraft:minecraft-provider-def:2.0.12-SNAPSHOT",
            includeScopes = setOf("compile", "runtime", "import")
        )

        runBlocking(Executors.newCachedThreadPool().asCoroutineDispatcher()) {
            archiveGraph.cache(
                request,
                SimpleMavenRepositorySettings.default("https://maven.extframework.dev/snapshots"),
                maven.resolver
            )
            archiveGraph.get(request.descriptor, maven.resolver)

            // BasicDependencyNode 5164
            val size = archiveGraph.nodes.size

            val result = archiveGraph.unload(
                request.descriptor
            )

            check(result.size == size)
            check(archiveGraph.nodes.isEmpty())

            delay(1000)

            System.gc()

            println("HERE")
        }

        println("Ending + " + archiveGraph)
    }
//dev.extframework:minecraft-bootstrapper:2.0.12-SNAPSHOT

    @Test
    fun `Test unloading ignores reused archives`() {

        val archiveGraph = ArchiveGraph.from(Path("test-run").toAbsolutePath())
        val maven = MavenResolverProvider()

        runBlocking(Executors.newCachedThreadPool().asCoroutineDispatcher()) {
            val toUnload = SimpleMavenArtifactRequest(
                "com.durganmcbroom:artifact-resolver-jvm:1.3-SNAPSHOT",
                includeScopes = setOf("compile", "runtime", "import")
            )

            archiveGraph.cache(
                toUnload,
                SimpleMavenRepositorySettings.default("https://maven.extframework.dev/snapshots"),
                maven.resolver
            )
            archiveGraph.get(toUnload.descriptor, maven.resolver)

            val toConstrain = SimpleMavenArtifactRequest(
                "dev.extframework:archives:1.5-SNAPSHOT",
                includeScopes = setOf("compile", "runtime", "import")
            )

            archiveGraph.cache(
                toConstrain,
                SimpleMavenRepositorySettings.default("https://maven.extframework.dev/snapshots"),
                maven.resolver
            )
            archiveGraph.get(toConstrain.descriptor, maven.resolver)

            val size = archiveGraph.nodes.size

            val result = archiveGraph.unload(
                toUnload.descriptor
            )

            // Shouldn't remove all
            check(result.size != size)
            println(result.joinToString("\n") {
                it.descriptor.name
            })
        }

        System.gc()
        println("Ending")
    }

    data class CustomType(val str: String)

    class WithSuspendRef<T>(
        val cb: suspend () -> T
    )

    suspend fun callThis(ref: WithSuspendRef<*>): Any? {
        delay(100)

        return ref.cb()
    }

    @Test
    fun `Coroutine mem manage`() {
        val withRef = WithSuspendRef {
            CustomType("A custom type")
        }

        runBlocking {
            println(callThis(withRef))

            println("Here")

            System.gc()

            delay(10)

            println("here now")
        }
    }


    @Test
    fun `Test throws constrained exception`() {

        val archiveGraph = ArchiveGraph.from(Path("test-run").toAbsolutePath())
        val maven = MavenResolverProvider()

        val request = SimpleMavenArtifactRequest(
            "dev.extframework.minecraft:minecraft-provider-def:2.0.12-SNAPSHOT",
            includeScopes = setOf("compile", "runtime", "import")
        )

        runBlocking(Executors.newCachedThreadPool().asCoroutineDispatcher()) {
            archiveGraph.cache(
                request,
                SimpleMavenRepositorySettings.default("https://maven.extframework.dev/snapshots"),
                maven.resolver
            )
            archiveGraph.get(request.descriptor, maven.resolver)

            val result = runCatching {
                archiveGraph.unload(
                    SimpleMavenDescriptor.parseDescription("com.durganmcbroom:jobs-jvm:1.3.2-SNAPSHOT")!!
                )
            }

            check(result.isFailure)
            check(result.exceptionOrNull() is ArchiveException.UnloadingConstrained)
        }
    }
}