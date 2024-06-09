package dev.extframework.boot.test.component

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.resources.ResourceAlgorithm
import dev.extframework.boot.BootInstance
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.archive.ArchiveTarget
import dev.extframework.boot.component.ComponentConfiguration
import dev.extframework.boot.component.ComponentFactory
import dev.extframework.boot.component.ComponentInstance
import dev.extframework.boot.component.SoftwareComponentResolver
import dev.extframework.boot.component.artifact.SoftwareComponentArtifactRequest
import dev.extframework.boot.component.artifact.SoftwareComponentDescriptor
import dev.extframework.boot.component.artifact.SoftwareComponentRepositorySettings
import dev.extframework.boot.dependency.BasicDependencyNode
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.boot.maven.MavenDependencyResolver
import dev.extframework.boot.maven.MavenResolverProvider
import dev.extframework.boot.test.dependency.prettyPrint
import dev.extframework.boot.test.dependency.separator
import runBootBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class TestLoadComponent {
    @Test
    fun `Test cache component`() {
        val basePath = Files.createTempDirectory("m2cache")

        val archiveGraph = ArchiveGraph(basePath,
            listOf(
                "dev.extframework:archives:1.1-SNAPSHOT",
                "dev.extframework:archives-mixin:1.1-SNAPSHOT",
                "io.arrow-kt:arrow-core:1.1.2",
                "org.jetbrains.kotlinx:kotlinx-cli:0.3.5",
                "dev.extframework:boot:2.0-SNAPSHOT",
                "com.durganmcbroom:artifact-resolver:1.0-SNAPSHOT",
                "com.durganmcbroom:artifact-resolver-jvm:1.0-SNAPSHOT",
                "com.durganmcbroom:artifact-resolver-simple-maven:1.0-SNAPSHOT",
                "com.durganmcbroom:artifact-resolver-simple-maven-jvm:1.0-SNAPSHOT",
                "net.bytebuddy:byte-buddy-agent:1.12.18",
                "dev.extframework:common-util:1.0-SNAPSHOT",
                "com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4",
                "dev.extframework:archive-mapper:1.2-SNAPSHOT",
                "dev.extframework:object-container:1.0-SNAPSHOT",
                "com.durganmcbroom:jobs:1.0-SNAPSHOT",
                "com.durganmcbroom:jobs-logging:1.0-SNAPSHOT",
                "com.durganmcbroom:jobs-progress:1.0-SNAPSHOT",
                "com.durganmcbroom:jobs-progress-simple:1.0-SNAPSHOT",
                "com.durganmcbroom:jobs-progress-simple-jvm:1.0-SNAPSHOT",
            ).map {
                SimpleMavenDescriptor.parseDescription(it)!!
            }.associateWithTo(HashMap()) {
                BasicDependencyNode(
                    it,
                    null,
                    setOf(),
                    object : ArchiveAccessTree {
                        override val descriptor: ArtifactMetadata.Descriptor = it
                        override val targets: List<ArchiveTarget> = listOf()
                    },
                    MavenDependencyResolver(
                        parentClassLoader = ClassLoader.getPlatformClassLoader(),
                    )
                )
            }
        )

        val dependencies = DependencyTypeContainer(archiveGraph)
        dependencies.register("simple-maven",
            MavenResolverProvider())
        val componentGraph = SoftwareComponentResolver(
            dependencies, object: BootInstance {
                override val location: Path
                    get() = TODO("Not yet implemented")
                override val dependencyTypes: DependencyTypeContainer
                    get() = TODO("Not yet implemented")
                override val archiveGraph: ArchiveGraph
                    get() = TODO("Not yet implemented")
                override val componentResolver: SoftwareComponentResolver
                    get() = TODO("Not yet implemented")

                override fun isCached(descriptor: SoftwareComponentDescriptor): Boolean {
                    TODO("Not yet implemented")
                }

                override fun cache(
                    request: SoftwareComponentArtifactRequest,
                    location: SoftwareComponentRepositorySettings
                ): Job<Unit> {
                    TODO("Not yet implemented")
                }

                override fun <T : ComponentConfiguration, I : ComponentInstance<T>> new(
                    descriptor: SoftwareComponentDescriptor,
                    factoryType: Class<out ComponentFactory<T, I>>,
                    configuration: T
                ): I {
                    TODO("Not yet implemented")
                }

            }, this::class.java.classLoader
        )


        val request = SimpleMavenArtifactRequest(
            "dev.extframework.components:ext-loader:1.0-SNAPSHOT",
            includeScopes = setOf("compile", "runtime", "import")
        )

        val node = runBootBlocking(JobName("test"))  {
            archiveGraph.cache(
                request,
                SimpleMavenRepositorySettings.local(
                    preferredHash = ResourceAlgorithm.SHA1
                ),
                componentGraph
            )().merge()

            archiveGraph.get(request.descriptor, componentGraph)().merge()
        }

        node.factory

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
