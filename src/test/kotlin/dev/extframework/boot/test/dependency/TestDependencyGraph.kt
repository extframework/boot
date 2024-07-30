package dev.extframework.boot.test.dependency

import BootLoggerFactory
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.launch
import com.durganmcbroom.jobs.result
import com.durganmcbroom.resources.ResourceAlgorithm
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.archive.ArchiveNode
import dev.extframework.boot.archive.DefaultArchiveGraph
import dev.extframework.boot.maven.MavenDependencyResolver
import dev.extframework.boot.maven.MavenResolverProvider
import dev.extframework.boot.util.printTree
import dev.extframework.boot.util.toGraphable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class TestDependencyGraph {
    @Test
    fun `Test maven basic dependency loading`() {
        val basePath = Files.createTempDirectory("m2cache")
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

        check(node.access.targets.size == 2) { "Wrong target size" }
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
        val maven = MavenResolverProvider()
        val archiveGraph = ArchiveGraph.from(Path.of("test-run").toAbsolutePath())
        println(archiveGraph.path)

        val request = SimpleMavenArtifactRequest(
            "dev.extframework.minecraft:minecraft-provider-def:1.0-SNAPSHOT",
            includeScopes = setOf("compile", "runtime", "import")
        )

        val node = launch(BootLoggerFactory()) {
            archiveGraph.cache(
                request,
                SimpleMavenRepositorySettings.local(),// .default("https://maven.extframework.dev/snapshots"),
                maven.resolver
            )().merge()
            archiveGraph.get(request.descriptor, maven.resolver)().merge()
        }

        println(node)
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