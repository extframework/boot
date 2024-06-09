package dev.extframework.boot.test.dependency

import com.durganmcbroom.artifact.resolver.MetadataRequestException
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.result
import com.durganmcbroom.resources.ResourceAlgorithm
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.maven.MavenDependencyResolver
import dev.extframework.boot.maven.MavenResolverProvider
import runBootBlocking
import java.nio.file.Files
import kotlin.test.Test

class TestDependencyGraph {
    @Test
    fun `Test maven basic dependency loading`() {
        val basePath = Files.createTempDirectory("m2cache")
        println("Base path: '$basePath'")
        val maven = MavenResolverProvider()
        val archiveGraph = ArchiveGraph(basePath, mutableMapOf())

        val request = SimpleMavenArtifactRequest(
            "dev.extframework.minecraft:minecraft-provider-def:1.0-SNAPSHOT",
            includeScopes = setOf("compile", "runtime", "import")
        )

        cacheAndGet(archiveGraph, request, SimpleMavenRepositorySettings.local(), maven.resolver)
    }

    @Test
    fun `Test maven dual dependency loading`() {
        val basePath = Files.createTempDirectory("m2cache")

        val maven = MavenResolverProvider()
        val archiveGraph = ArchiveGraph(basePath)

        val request = SimpleMavenArtifactRequest(
            "dev.extframework.minecraft:minecraft-provider-def:1.0-SNAPSHOT",
            includeScopes = setOf("compile", "runtime", "import")
        )

        cacheAndGet(
            archiveGraph, request, SimpleMavenRepositorySettings.local(
                preferredHash = ResourceAlgorithm.SHA1
            ), maven.resolver
        )

        separator("Second request:")

        val secondRequest = SimpleMavenArtifactRequest(
            "io.arrow-kt:arrow-core:1.2.1",
            includeScopes = setOf("compile", "runtime", "import")
        )

        cacheAndGet(
            archiveGraph, secondRequest, SimpleMavenRepositorySettings.mavenCentral(
                preferredHash = ResourceAlgorithm.SHA1
            ), maven.resolver
        )
    }

    @Test
    fun `Test invalid artifact throws correct exception`() {
        val basePath = Files.createTempDirectory("m2cache")

        val maven = MavenResolverProvider()
        val archiveGraph = ArchiveGraph(basePath)


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
        check(r.exceptionOrNull()?.cause is MetadataRequestException) { "" }
    }

    @Test
    fun `Test getting without caching throws correct exception`() {
        val basePath = Files.createTempDirectory("m2cache")

        val maven = MavenResolverProvider()
        val archiveGraph = ArchiveGraph(basePath)


        val request = SimpleMavenArtifactRequest(
            "does:not:exist",
            includeScopes = setOf("compile", "runtime", "import")
        )

        val r = runBootBlocking {
            archiveGraph.get(request.descriptor, maven.resolver)()
        }

        r.exceptionOrNull()?.printStackTrace()
        check(r.exceptionOrNull()?.cause is ArchiveException.ArchiveNotCached)
    }

    private fun cacheAndGet(
        archiveGraph: ArchiveGraph,
        request: SimpleMavenArtifactRequest,
        repository: SimpleMavenRepositorySettings,
        maven: MavenDependencyResolver
    ) {
        val node = runBootBlocking(JobName("test")) {
            archiveGraph.cache(
                request,
                repository,
                maven
            )().merge()

            archiveGraph.get(request.descriptor, maven)().merge()
        }

        node.prettyPrint { handle, depth ->
            val str = (0..depth).joinToString(separator = "   ") { "" } + handle.descriptor.name
            println(str)
        }
        separator("Targets:")
        println(node.access.targets.joinToString(separator = "\n") {
            it.descriptor.name
        })
    }
}