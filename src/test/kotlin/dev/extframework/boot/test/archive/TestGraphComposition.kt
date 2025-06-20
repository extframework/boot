package dev.extframework.boot.test.archive

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import dev.extframework.boot.archive.ChildDefaultArchiveGraph
import dev.extframework.boot.archive.DefaultArchiveGraph
import dev.extframework.boot.maven.MavenResolverProvider
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import kotlin.io.path.Path
import kotlin.test.Test

class TestGraphComposition {
    val basePath = Path("test-run").toAbsolutePath()
    val baseGraph = DefaultArchiveGraph(basePath)
    val maven = MavenResolverProvider()

    @Test
    fun `Test create sub graph and load`() {
        val subGraph = ChildDefaultArchiveGraph(baseGraph)

        val sampleRequest = SimpleMavenArtifactRequest(
            "dev.extframework.minecraft:minecraft-provider-def:2.0.12-SNAPSHOT",
            includeScopes = setOf("compile", "runtime", "import")
        )

        runBlocking(Executors.newCachedThreadPool().asCoroutineDispatcher()) {
            baseGraph.cache(
                sampleRequest,
                SimpleMavenRepositorySettings.default("https://maven.extframework.dev/snapshots"),
                maven.resolver
            )

            val higherSampleDescriptor = SimpleMavenDescriptor.parseDescription(
                "dev.extframework:archives:1.5-SNAPSHOT",
            )!!

            baseGraph.get(higherSampleDescriptor, maven.resolver)

            subGraph.get(sampleRequest.descriptor, maven.resolver)

            println("Here")
        }
    }

    @Test
    fun `Test unloading from sub graph`() {
        val subGraph = ChildDefaultArchiveGraph(baseGraph)

        val sampleRequest = SimpleMavenArtifactRequest(
            "dev.extframework.minecraft:minecraft-provider-def:2.0.12-SNAPSHOT",
            includeScopes = setOf("compile", "runtime", "import")
        )

        runBlocking(Executors.newCachedThreadPool().asCoroutineDispatcher()) {
            baseGraph.cache(
                sampleRequest,
                SimpleMavenRepositorySettings.default("https://maven.extframework.dev/snapshots"),
                maven.resolver
            )

            val higherSampleDescriptor = SimpleMavenDescriptor.parseDescription(
                "dev.extframework:archives:1.5-SNAPSHOT",
            )!!

            baseGraph.get(higherSampleDescriptor, maven.resolver)

            subGraph.registerResolver(maven.resolver)
            subGraph.get(sampleRequest.descriptor, maven.resolver)

            subGraph.unload(sampleRequest.descriptor)

            println("Here")
        }
    }
}