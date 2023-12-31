package net.yakclient.boot.test

import bootFactories
import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.JobResult
import kotlinx.coroutines.runBlocking
import net.yakclient.archives.ArchiveFinder
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.boot.BootInstance
import net.yakclient.boot.archive.ArchiveException
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.archive.ArchiveNode
import net.yakclient.boot.archive.BasicArchiveResolutionProvider
import net.yakclient.boot.component.*
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.artifact.SoftwareComponentRepositorySettings
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.boot.dependency.DependencyTypeContainer
import net.yakclient.boot.main.createMavenProvider
import net.yakclient.common.util.resolve
import orThrow
import java.lang.reflect.Constructor
import java.nio.file.Files
import java.nio.file.Path

public fun testBootInstance(
    components: Map<SoftwareComponentDescriptor, Class<out ComponentFactory<*, *>>>,
    location: Path = Files.createTempDirectory("boot-test"),
    dependencies: Set<ArtifactMetadata.Descriptor> = emptySet(),
    dependencyNodeCreator: (ArtifactMetadata.Descriptor) -> ArchiveNode<*> = {
        DependencyNode(
            null,
            emptySet(),
            it
        )
    }
): BootInstance {
    class TestResolver(
        boot: BootInstance
    ) : SoftwareComponentResolver(
        BasicArchiveResolutionProvider(
            Archives.Finders.ZIP_FINDER as ArchiveFinder<ArchiveReference>,
            Archives.Resolvers.ZIP_RESOLVER
        ),
        boot.dependencyTypes,
        boot
    )

    return object : BootInstance {
        override val location: Path = location
        override val archiveGraph: ArchiveGraph = ArchiveGraph(
            location resolve "archives",
            components.mapValuesTo(HashMap<ArtifactMetadata.Descriptor, ArchiveNode<*>>()) {
                SoftwareComponentNode(
                    it.key,
                    null,
                    setOf(),
                    setOf(),
                    SoftwareComponentModel(
                        "",
                        "", null, listOf(), listOf()
                    ),
                    run {
                        fun <T : Any> Class<T>.tryGetConstructor(vararg params: Class<*>): Constructor<T>? =
                            net.yakclient.common.util.runCatching(NoSuchMethodException::class) { this.getConstructor(*params) }

                        fun loadFactory(cls: Class<out ComponentFactory<*, *>>): ComponentFactory<*, *> {
                            return (cls.tryGetConstructor(BootInstance::class.java)?.newInstance(this)
                                ?: cls.tryGetConstructor()?.newInstance()) as ComponentFactory<*, *>
                        }

                        loadFactory(it.value)
                    }
                )
            }.apply {
                putAll(dependencies.associateWith {
                    dependencyNodeCreator(it)
                })
            })
        override val dependencyTypes: DependencyTypeContainer = DependencyTypeContainer(archiveGraph)
        override val componentResolver: SoftwareComponentResolver = TestResolver(this)

        init {
            archiveGraph.registerResolver(componentResolver)
            dependencyTypes.register("simple-maven", createMavenProvider())
        }

        override fun isCached(descriptor: SoftwareComponentDescriptor): Boolean {
            return archiveGraph.contains(descriptor)
        }

        override suspend fun cache(
            request: SoftwareComponentArtifactRequest,
            location: SoftwareComponentRepositorySettings
        ): JobResult<Unit, ArchiveException> {
            return archiveGraph.cache(request, location, componentResolver)
        }

        override fun <T : ComponentConfiguration, I : ComponentInstance<T>> new(
            descriptor: SoftwareComponentDescriptor,
            factoryType: Class<out ComponentFactory<T, I>>,
            configuration: T
        ): I {
            return runBlocking(bootFactories() + JobName("New test component: '$descriptor'")) {
                val it = archiveGraph.get(descriptor, componentResolver).orThrow()

                check(factoryType.isInstance(it.factory))

                ((it.factory as? ComponentFactory<T, I>)?.new(configuration)
                    ?: throw IllegalArgumentException("Cannot start a Component with no factory, you must start its children instead. Use the Software component graph to do this."))
            }
        }
    }
}