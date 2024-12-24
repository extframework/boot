package dev.extframework.boot.test.dependency

import BootLoggerFactory
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.launch
import com.durganmcbroom.jobs.result
import com.durganmcbroom.resources.KtorInstance
import com.durganmcbroom.resources.RemoteResource
import com.durganmcbroom.resources.ResourceAlgorithm
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.archive.ArchiveNode
import dev.extframework.boot.archive.DefaultArchiveGraph
import dev.extframework.boot.maven.MavenDependencyResolver
import dev.extframework.boot.maven.MavenResolverProvider
import dev.extframework.boot.util.printTree
import dev.extframework.boot.util.toGraphable
import dev.extframework.common.util.copyTo
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.debug.CoroutinesBlockHoundIntegration
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import reactor.blockhound.BlockHound
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.Path
import kotlin.test.Test

class TestDependencyGraph {
    @Test
    fun `Test maven basic dependency loading`() {
        val basePath = Path("test-run")
        val maven = MavenResolverProvider()
        val archiveGraph = DefaultArchiveGraph(basePath, mutableMapOf())

        val request = SimpleMavenArtifactRequest(
            "org.ow2.asm:asm-commons:9.7",
            includeScopes = setOf("compile", "runtime", "import")
        )

        val node = cacheAndGet(
            archiveGraph,
            request,
            SimpleMavenRepositorySettings.local(path = this::class.java.getResource("/blackbox-repository")!!.path),
            maven.resolver
        )

        check(node.access.targets.size == 3) { "Wrong target size" }
        check(node.access.targets.mapTo(HashSet()) { it.descriptor.name }
            .containsAll(setOf("org.ow2.asm:asm:9.7", "org.ow2.asm:asm-tree:9.7"))) { "Wrong targets" }
    }

    @Test
    fun `Test invalid artifact throws correct exception`() {
        val basePath = Files.createTempDirectory("m2cache")

        val maven = MavenResolverProvider()
        val archiveGraph = DefaultArchiveGraph(basePath)


        val request = SimpleMavenArtifactRequest(
            "does:not:exist",
            includeScopes = setOf("compile", "runtime", "import")
        )

        val r = result {
            cacheAndGet(
                archiveGraph, request, SimpleMavenRepositorySettings.mavenCentral(
                    preferredHash = ResourceAlgorithm.SHA1
                ), maven.resolver
            )
        }


        r.exceptionOrNull()?.printStackTrace()
        check(r.exceptionOrNull() is ArchiveException.ArchiveNotFound) { "" }
    }

    @Test
    fun `Test getting without caching throws correct exception`() {
        val basePath = Files.createTempDirectory("m2cache")

        val maven = MavenResolverProvider()
        val archiveGraph = DefaultArchiveGraph(basePath)


        val request = SimpleMavenArtifactRequest(
            "does:not:exist",
            includeScopes = setOf("compile", "runtime", "import")
        )

        val r = launch(BootLoggerFactory()) {
            archiveGraph.get(request.descriptor, maven.resolver)()
        }

        r.exceptionOrNull()?.printStackTrace()
        check(r.exceptionOrNull() is ArchiveException.ArchiveNotCached)
    }

    @Test
    fun `Test bootstrapper dependency load`() {
        BlockHound.install(CoroutinesBlockHoundIntegration())

        val maven = MavenResolverProvider()

        val archiveGraph = ArchiveGraph.from(Path("test-run").toAbsolutePath())
        println(archiveGraph.path)

        val request = SimpleMavenArtifactRequest(
            "dev.extframework.minecraft:minecraft-provider-def:2.0.12-SNAPSHOT",
            includeScopes = setOf("compile", "runtime", "import")
        )

        val node = launch(BootLoggerFactory()) {
            runBlocking(Executors.newCachedThreadPool().asCoroutineDispatcher()) {
                archiveGraph.cacheAsync(
                    request,
                    SimpleMavenRepositorySettings.default("https://maven.extframework.dev/snapshots"),
                    maven.resolver
                )().merge()
                archiveGraph.getAsync(request.descriptor, maven.resolver)().merge()
            }

        }

        println(node)
    }

    @Test
    fun `Speed test`() {
        val url =
            URL("https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-reflect/1.5.30/kotlin-reflect-1.5.30.jar")

        val file = File("test-run/speed-test.jar")
        runSpeedTest {
            val stream = url.openStream()
            val streamOut = file.outputStream()
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)

            while (true) {
                if (stream.read(buf) == -1) break
                streamOut.write(buf)
            }
        }

        runBlocking {
            val client = KtorInstance.client

            runSpeedTest {
                val res = client.get(HttpRequestBuilder().apply { url(url) })
                val channel: ByteReadChannel = res.body()
                val streamOut = file.outputStream()

                while (!channel.isClosedForRead) {
                    val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                    while (!packet.exhausted()) {
                        val bytes = packet.readByteArray()

                        streamOut.write(bytes)
                    }
                }
            }
        }

        runBlocking {
            runSpeedTest {

                val resource = RemoteResource(HttpRequestBuilder().apply { url(url) })
                resource copyTo file.toPath()
            }
        }
    }

    private inline fun runSpeedTest(
        block: () -> Unit,
    ) {
        val iterations = 20


        var total = 0L
        for (i in 0..iterations) {
            val start = System.currentTimeMillis()
            block.invoke()
            total += System.currentTimeMillis() - start
            println(i)
        }
        println(total / iterations)
    }

    companion object {
        fun cacheAndGet(
            archiveGraph: ArchiveGraph,
            request: SimpleMavenArtifactRequest,
            repository: SimpleMavenRepositorySettings,
            maven: MavenDependencyResolver
        ): ArchiveNode<*> {
            val node = launch(JobName("test") + BootLoggerFactory()) {
                archiveGraph.cache(
                    request,
                    repository,
                    maven
                )().merge()

                archiveGraph.get(request.descriptor, maven)().merge()
            }

            printTree(node.toGraphable())
            separator("Targets:")
            println(node.access.targets.joinToString(separator = "\n") {
                it.descriptor.name
            })

            return node
        }
    }
}