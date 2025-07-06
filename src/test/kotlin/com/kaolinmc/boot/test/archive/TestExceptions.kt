package com.kaolinmc.boot.test.archive

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.kaolinmc.boot.archive.ArchiveException
import com.kaolinmc.boot.archive.ArchiveGraph
import com.kaolinmc.boot.dependency.BasicDependencyNode
import com.kaolinmc.boot.maven.MavenResolverProvider
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test

class TestExceptions {
    val basePath = Files.createTempDirectory("m2cache")
    val archiveGraph = ArchiveGraph.from(basePath)
    val maven = MavenResolverProvider()

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
    ): BasicDependencyNode<*> {
        val node = runBlocking {
            archiveGraph.cache(
                request,
                repository,
                maven.resolver
            )

            archiveGraph.get(request.descriptor, maven.resolver)
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
            runBlocking {
                archiveGraph.get(
                    SimpleMavenDescriptor.parseDescription("a:a:a")!!,
                    maven.resolver
                )
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