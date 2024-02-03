package net.yakclient.boot.test.component

import bootFactories
import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.JobResult
import kotlinx.coroutines.runBlocking
import net.yakclient.boot.BootInstance
import net.yakclient.boot.archive.ArchiveAccessTree
import net.yakclient.boot.archive.ArchiveException
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.archive.ArchiveTarget
import net.yakclient.boot.component.ComponentConfiguration
import net.yakclient.boot.component.ComponentFactory
import net.yakclient.boot.component.ComponentInstance
import net.yakclient.boot.component.SoftwareComponentResolver
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.artifact.SoftwareComponentRepositorySettings
import net.yakclient.boot.dependency.BasicDependencyNode
import net.yakclient.boot.dependency.DependencyTypeContainer
import net.yakclient.boot.main.createMavenDependencyGraph
import net.yakclient.boot.main.createMavenProvider
import net.yakclient.boot.maven.MavenDependencyResolver
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.test.dependency.prettyPrint
import net.yakclient.boot.test.dependency.separator
import orThrow
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class TestLoadComponent {
    @Test
    fun `Test cache component`() {
        val basePath = Files.createTempDirectory("m2cache")

        val archiveGraph = ArchiveGraph(basePath,
            listOf(
                "net.yakclient:archives:1.1-SNAPSHOT",
                "net.yakclient:archives-mixin:1.1-SNAPSHOT",
                "io.arrow-kt:arrow-core:1.1.2",
                "org.jetbrains.kotlinx:kotlinx-cli:0.3.5",
                "net.yakclient:boot:2.0-SNAPSHOT",
                "com.durganmcbroom:artifact-resolver:1.0-SNAPSHOT",
                "com.durganmcbroom:artifact-resolver-jvm:1.0-SNAPSHOT",
                "com.durganmcbroom:artifact-resolver-simple-maven:1.0-SNAPSHOT",
                "com.durganmcbroom:artifact-resolver-simple-maven-jvm:1.0-SNAPSHOT",
                "net.bytebuddy:byte-buddy-agent:1.12.18",
                "net.yakclient:common-util:1.0-SNAPSHOT",
                "com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4",
                "net.yakclient:archive-mapper:1.2-SNAPSHOT",
                "net.yakclient:object-container:1.0-SNAPSHOT",
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
                        override val targets: Set<ArchiveTarget> = setOf()
                    },
                    MavenDependencyResolver(
                        parentClassLoader = ClassLoader.getPlatformClassLoader(),
                    )
                )
            }
        )

        val dependencies = DependencyTypeContainer(archiveGraph)
        dependencies.register("simple-maven",
            createMavenProvider())
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

                override suspend fun cache(
                    request: SoftwareComponentArtifactRequest,
                    location: SoftwareComponentRepositorySettings
                ): JobResult<Unit, ArchiveException> {
                    TODO("Not yet implemented")
                }

                override fun <T : ComponentConfiguration, I : ComponentInstance<T>> new(
                    descriptor: SoftwareComponentDescriptor,
                    factoryType: Class<out ComponentFactory<T, I>>,
                    configuration: T
                ): I {
                    TODO("Not yet implemented")
                }

            }, this::class.java.classLoader, PrivilegeManager(null, PrivilegeAccess.emptyPrivileges())
        )


        val request = SimpleMavenArtifactRequest(
            "net.yakclient.components:ext-loader:1.0-SNAPSHOT",
            includeScopes = setOf("compile", "runtime", "import")
        )



        val node = runBlocking(bootFactories() + JobName("test")) {
            archiveGraph.cache(
                request,
                SimpleMavenRepositorySettings.local(
                    preferredHash = HashType.SHA1
                ),
                componentGraph
            ).orThrow()

            archiveGraph.get(request.descriptor, componentGraph)
        }.orThrow()

        node.factory

        node.prettyPrint { handle, depth ->
            val str = (0..depth).joinToString(separator = "   ") { "" } + handle.descriptor.name
            println(str)
        }
        separator()
        println(node.access.targets.joinToString(separator = "\n") {
            it.descriptor.name
        })
    }
}
