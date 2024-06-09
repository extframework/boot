package dev.extframework.boot.test.archive

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.JobName
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.dependency.BasicDependencyNode
import dev.extframework.boot.maven.MavenResolverProvider
import runBootBlocking
import java.nio.file.Files
import kotlin.test.Test

class TestExceptions {
    val basePath = Files.createTempDirectory("m2cache")
    val maven = MavenResolverProvider()
    val archiveGraph = ArchiveGraph(basePath, mutableMapOf())

    inline fun <reified T : Throwable> assertThatThrows(
        noinline exp: () -> Unit,
    ) {
        assertThatThrows(exp, T::class.java)
    }

    @Throws(Exception::class)
    fun assertThatThrows(
        exp: () -> Unit,
        throwable: Class<out Throwable>
    ) {
        val r = runCatching {
            exp()
        }
        if (!r.isFailure) throw Exception("Expression should fail!")
        r.exceptionOrNull()?.printStackTrace()
        val thrownCls = r.exceptionOrNull()!!::class.java
        if (throwable != thrownCls)
            throw Exception("Invalid exception thrown from expression! Expected it to throw: '$throwable' but it threw '$thrownCls'")
    }

    fun loadArtifact(
        request: SimpleMavenArtifactRequest,
        repository: SimpleMavenRepositorySettings,
    ): BasicDependencyNode {
        val node = runBootBlocking(JobName("test")) {
            archiveGraph.cache(
                request,
                repository,
                maven.resolver
            )().merge()

            archiveGraph.get(request.descriptor, maven.resolver)().merge()
        }

        return node
    }

    @Test
    fun `Test artifact not found throws correctly`() {
        assertThatThrows<ArchiveException.ArchiveNotFound> {
            loadArtifact(
                SimpleMavenArtifactRequest("a:a:a"),
                SimpleMavenRepositorySettings.mavenCentral()
            )
        }
    }

    @Test
    fun `Test artifact not cached throws correctly`() {
        assertThatThrows<ArchiveException.ArchiveNotCached> {
            runBootBlocking {
                archiveGraph.get(
                    SimpleMavenDescriptor.parseDescription("a:a:a")!!,
                    maven.resolver
                )().merge()
            }
        }
    }

    @Test
    fun `Test illegal artifact repository throws correctly`() {
        assertThatThrows<ArchiveException> {
            loadArtifact(
                SimpleMavenArtifactRequest("a:a:a"),
                SimpleMavenRepositorySettings.default("")
            )
        }
    }
}