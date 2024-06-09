package dev.extframework.boot.test

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.FailingJob
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import dev.extframework.boot.BootInstance
import dev.extframework.boot.archive.*
import dev.extframework.boot.component.*
import dev.extframework.boot.component.artifact.SoftwareComponentArtifactMetadata
import dev.extframework.boot.component.artifact.SoftwareComponentArtifactRequest
import dev.extframework.boot.component.artifact.SoftwareComponentDescriptor
import dev.extframework.boot.component.artifact.SoftwareComponentRepositorySettings
import dev.extframework.boot.dependency.BasicDependencyNode
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.boot.maven.MavenResolverProvider
import dev.extframework.common.util.resolve
import runBootBlocking
import java.lang.reflect.Constructor
import java.nio.file.Files
import java.nio.file.Path

public fun interface ArchiveNodeCreator<T : ArchiveNode<T>> :
        (ArtifactMetadata.Descriptor, ArchiveNodeResolver<*, *, T, *, *>) -> T

public fun testBootInstance(
    components: Map<SoftwareComponentDescriptor, Class<out ComponentFactory<*, *>>> = mapOf(),
    location: Path = Files.createTempDirectory("boot-test"),
    // Versions will be ignored
    dependencies: Set<ArtifactMetadata.Descriptor> = emptySet(),
): BootInstance {
    return testBootInstance(
        BasicDependencyNode::class.java,
        components,
        location,
        dependencies
    ) { it, resolver ->
        BasicDependencyNode(
            it,
            null,
            emptySet(),
            emptyAccessTree(it),
            resolver,
        )
    }
}

public fun <T : ArchiveNode<T>> testBootInstance(
    nodeType: Class<T>,
    components: Map<SoftwareComponentDescriptor, Class<out ComponentFactory<*, *>>> = mapOf(),
    location: Path = Files.createTempDirectory("boot-test"),
    // Versions will be ignored
    dependencies: Set<ArtifactMetadata.Descriptor> = emptySet(),
    dependencyNodeCreator: ArchiveNodeCreator<T>
): BootInstance {
    class TestResolver(
        boot: BootInstance
    ) : SoftwareComponentResolver(
        boot.dependencyTypes,
        boot,
        BootInstance::class.java.classLoader,
    )

    val mockDependencyResolver = mockDependencyResolver(nodeType)

    return object : BootInstance {
        override val location: Path = location

        override val archiveGraph: ArchiveGraph by lazy {
            ArchiveGraph(
                location resolve "archives",
                (components.map { it ->

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
                                dev.extframework.common.util.runCatching(NoSuchMethodException::class) {
                                    this.getConstructor(
                                        *params
                                    )
                                }

                            fun loadFactory(cls: Class<out ComponentFactory<*, *>>): ComponentFactory<*, *> {
                                return (cls.tryGetConstructor(BootInstance::class.java)?.newInstance(this)
                                    ?: cls.tryGetConstructor()?.newInstance()) as ComponentFactory<*, *>
                            }

                            loadFactory(it.value)
                        },
                        emptyAccessTree(it.key),
                        mockComponentResolver
                    )
                } + dependencies.map {
                    dependencyNodeCreator(
                        it,
                        mockDependencyResolver
                    )
                }).let(::createArchiveGraphMap)
            )
        }
        override val dependencyTypes: DependencyTypeContainer = DependencyTypeContainer(archiveGraph)
        override val componentResolver: SoftwareComponentResolver = TestResolver(this)

        init {
            archiveGraph.registerResolver(componentResolver)
            dependencyTypes.register("simple-maven", MavenResolverProvider())
        }

        override fun isCached(descriptor: SoftwareComponentDescriptor): Boolean {
            return archiveGraph.contains(descriptor)
        }

        override fun cache(
            request: SoftwareComponentArtifactRequest,
            location: SoftwareComponentRepositorySettings
        ): Job<Unit> {
            return archiveGraph.cache(request, location, componentResolver)
        }

        override fun <T : ComponentConfiguration, I : ComponentInstance<T>> new(
            descriptor: SoftwareComponentDescriptor,
            factoryType: Class<out ComponentFactory<T, I>>,
            configuration: T
        ): I {
            return runBootBlocking(JobName("New test component: '$descriptor'")) {
                val it = archiveGraph.get(descriptor, componentResolver)().merge()

                check(factoryType.isInstance(it.factory))

                ((it.factory as? ComponentFactory<T, I>)?.new(configuration)
                    ?: throw IllegalArgumentException("Cannot start a Component with no factory, you must start its children instead. Use the Software component graph to do this."))
            }
        }
    }
}

public fun createArchiveGraphMap(
    nodes: List<ArchiveNode<*>>,
): MutableMap<ArtifactMetadata.Descriptor, ArchiveNode<*>> {
    val mappedNodes = nodes.associateBy {
        val descriptor = it.descriptor
        if (descriptor is SimpleMavenDescriptor) "${descriptor.group}:${descriptor.artifact}"
        else descriptor.name
    }

    val delegate = HashMap<ArtifactMetadata.Descriptor, ArchiveNode<*>>()

    return object : MutableMap<ArtifactMetadata.Descriptor, ArchiveNode<*>> by delegate {
        override fun get(key: ArtifactMetadata.Descriptor): ArchiveNode<*>? {
            val n = if (key is SimpleMavenDescriptor) "${key.group}:${key.artifact}" else key.name
            return mappedNodes[n] ?: delegate[key]
        }
    }
}

public fun emptyAccessTree(it: ArtifactMetadata.Descriptor): ArchiveAccessTree =
    object : ArchiveAccessTree {
        override val descriptor: ArtifactMetadata.Descriptor = it
        override val targets: List<ArchiveTarget> = listOf()
    }

private fun <T : ArchiveNode<T>> mockDependencyResolver(nodeType: Class<T>) = object :
    ArchiveNodeResolver<ArtifactMetadata.Descriptor, ArtifactRequest<ArtifactMetadata.Descriptor>, T, RepositorySettings, ArtifactMetadata<ArtifactMetadata.Descriptor, *>> {
    override val name: String = "test-resolver"
    override val nodeType: Class<T> = nodeType
    override val metadataType: Class<ArtifactMetadata<ArtifactMetadata.Descriptor, *>> =
        ArtifactMetadata::class.java as Class<ArtifactMetadata<ArtifactMetadata.Descriptor, *>>

    override fun createContext(settings: RepositorySettings): ResolutionContext<ArtifactRequest<ArtifactMetadata.Descriptor>, *, ArtifactMetadata<ArtifactMetadata.Descriptor, *>, *> {
        throw UnsupportedOperationException()
    }

    override fun deserializeDescriptor(descriptor: Map<String, String>, trace: ArchiveTrace): Result<ArtifactMetadata.Descriptor> {
        return Result.failure(UnsupportedOperationException("In test context"))
    }

    override fun cache(
        metadata: ArtifactMetadata<ArtifactMetadata.Descriptor, *>,
        helper: ArchiveCacheHelper<ArtifactMetadata.Descriptor>
    ): Job<ArchiveData<ArtifactMetadata.Descriptor, CacheableArchiveResource>> {
        return FailingJob { UnsupportedOperationException("In test context") }

    }

    override fun load(
        data: ArchiveData<ArtifactMetadata.Descriptor, CachedArchiveResource>,
        helper: ResolutionHelper
    ): Job<T> {
        return FailingJob { UnsupportedOperationException("In test context") }
    }

    override fun pathForDescriptor(
        descriptor: ArtifactMetadata.Descriptor,
        classifier: String,
        type: String
    ): Path {
        return Path.of("")
    }

    override fun serializeDescriptor(descriptor: ArtifactMetadata.Descriptor): Map<String, String> {
        return mapOf()
    }
}

private val mockComponentResolver = object :
    ArchiveNodeResolver<SoftwareComponentDescriptor, SoftwareComponentArtifactRequest, SoftwareComponentNode, SoftwareComponentRepositorySettings, SoftwareComponentArtifactMetadata> {
    override val name: String = "test-mock-component-resolver"
    override val nodeType: Class<SoftwareComponentNode> = SoftwareComponentNode::class.java
    override val metadataType: Class<SoftwareComponentArtifactMetadata> =
        SoftwareComponentArtifactMetadata::class.java

    override fun createContext(settings: SoftwareComponentRepositorySettings): ResolutionContext<SoftwareComponentArtifactRequest, *, SoftwareComponentArtifactMetadata, *> {
        TODO("Not yet implemented")
    }

    override fun deserializeDescriptor(descriptor: Map<String, String>, trace: ArchiveTrace): Result<SoftwareComponentDescriptor> {
        return Result.failure(UnsupportedOperationException("In test context"))
    }

    override fun cache(
        metadata: SoftwareComponentArtifactMetadata,
        helper: ArchiveCacheHelper<SoftwareComponentDescriptor>
    ): Job<ArchiveData<SoftwareComponentDescriptor, CacheableArchiveResource>> {
        return FailingJob { (UnsupportedOperationException("In test context")) }
    }

    override fun load(
        data: ArchiveData<SoftwareComponentDescriptor, CachedArchiveResource>,
        helper: ResolutionHelper
    ): Job<SoftwareComponentNode> {
        return FailingJob { UnsupportedOperationException("In test context") }
    }

    override fun pathForDescriptor(
        descriptor: SoftwareComponentDescriptor,
        classifier: String,
        type: String
    ): Path {
        return Path.of("")
    }

    override fun serializeDescriptor(descriptor: SoftwareComponentDescriptor): Map<String, String> {
        return mapOf()
    }

}