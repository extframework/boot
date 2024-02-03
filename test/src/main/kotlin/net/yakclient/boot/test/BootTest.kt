package net.yakclient.boot.test

import bootFactories
import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.JobResult
import kotlinx.coroutines.runBlocking
import net.yakclient.archives.ArchiveFinder
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.boot.BootInstance
import net.yakclient.boot.archive.*
import net.yakclient.boot.component.*
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactMetadata
import net.yakclient.boot.component.artifact.SoftwareComponentArtifactRequest
import net.yakclient.boot.component.artifact.SoftwareComponentDescriptor
import net.yakclient.boot.component.artifact.SoftwareComponentRepositorySettings
import net.yakclient.boot.dependency.BasicDependencyNode
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.boot.dependency.DependencyTypeContainer
import net.yakclient.boot.main.createMavenProvider
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.security.Privileges
import net.yakclient.common.util.LazyMap
import net.yakclient.common.util.resolve
import orThrow
import java.lang.reflect.Constructor
import java.nio.file.Files
import java.nio.file.Path

public fun interface ArchiveNodeCreator<T: ArchiveNode<T>> : (ArtifactMetadata.Descriptor, ArchiveNodeResolver<*, *, T, *, *>) -> T

public fun testBootInstance(
    components: Map<SoftwareComponentDescriptor, Class<out ComponentFactory<*, *>>> = mapOf(),
    location: Path = Files.createTempDirectory("boot-test"),
    dependencies: Set<ArtifactMetadata.Descriptor> = emptySet(),
)  : BootInstance {
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

public fun <T: ArchiveNode<T>> testBootInstance(
    nodeType: Class<T>,
    components: Map<SoftwareComponentDescriptor, Class<out ComponentFactory<*, *>>> = mapOf(),
    location: Path = Files.createTempDirectory("boot-test"),
    dependencies: Set<ArtifactMetadata.Descriptor> = emptySet(),
    dependencyNodeCreator: ArchiveNodeCreator<T>
): BootInstance {
    class TestResolver(
        boot: BootInstance
    ) : SoftwareComponentResolver(
        boot.dependencyTypes,
        boot,
        BootInstance::class.java.classLoader,
        PrivilegeManager(null, PrivilegeAccess.emptyPrivileges())
    )

    return object : BootInstance {
        override val location: Path = location

        override val archiveGraph: ArchiveGraph by lazy {
            ArchiveGraph(
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
                                net.yakclient.common.util.runCatching(NoSuchMethodException::class) {
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
                        object : ArchiveNodeResolver<SoftwareComponentDescriptor, SoftwareComponentArtifactRequest, SoftwareComponentNode, SoftwareComponentRepositorySettings, SoftwareComponentArtifactMetadata> {
                            override val name: String = "test-mock-component-resolver"
                            override val nodeType: Class<SoftwareComponentNode> = SoftwareComponentNode::class.java
                            override val metadataType: Class<SoftwareComponentArtifactMetadata> = SoftwareComponentArtifactMetadata::class.java
                            override val factory: RepositoryFactory<SoftwareComponentRepositorySettings, SoftwareComponentArtifactRequest, *, ArtifactReference<SoftwareComponentArtifactMetadata, *>, *>
                                get() = TODO("Not yet implemented")

                            override suspend fun deserializeDescriptor(descriptor: Map<String, String>): JobResult<SoftwareComponentDescriptor, ArchiveException> {
                                return JobResult.Failure(ArchiveException.NotSupported("In test context", trace()))

                            }

                            override suspend fun cache(
                                metadata: SoftwareComponentArtifactMetadata,
                                helper: ArchiveCacheHelper<SoftwareComponentDescriptor>
                            ): JobResult<ArchiveData<SoftwareComponentDescriptor, CacheableArchiveResource>, ArchiveException> {
                                return JobResult.Failure(ArchiveException.NotSupported("In test context", trace()))

                            }

                            override suspend fun load(
                                data: ArchiveData<SoftwareComponentDescriptor, CachedArchiveResource>,
                                helper: ResolutionHelper
                            ): JobResult<SoftwareComponentNode, ArchiveException> {
                                return JobResult.Failure(ArchiveException.NotSupported("In test context", trace()))

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
                    )
                }.apply {
                    putAll(dependencies.associateWith {
                        dependencyNodeCreator(
                            it,
                            object :
                                ArchiveNodeResolver<ArtifactMetadata.Descriptor, ArtifactRequest<ArtifactMetadata.Descriptor>, T, RepositorySettings, ArtifactMetadata<ArtifactMetadata.Descriptor, *>> {
                                override val name: String = "test-resolver"
                                override val nodeType: Class<T> = nodeType
                                override val metadataType: Class<ArtifactMetadata<ArtifactMetadata.Descriptor, *>> =
                                    ArtifactMetadata::class.java as Class<ArtifactMetadata<ArtifactMetadata.Descriptor, *>>
                                override val factory: RepositoryFactory<RepositorySettings, ArtifactRequest<ArtifactMetadata.Descriptor>, *, ArtifactReference<ArtifactMetadata<ArtifactMetadata.Descriptor, *>, *>, *>
                                    get() = TODO("Not yet implemented")

                                override suspend fun deserializeDescriptor(descriptor: Map<String, String>): JobResult<ArtifactMetadata.Descriptor, ArchiveException> {
                                    return JobResult.Failure(ArchiveException.NotSupported("In test context", trace()))
                                }

                                override suspend fun cache(
                                    metadata: ArtifactMetadata<ArtifactMetadata.Descriptor, *>,
                                    helper: ArchiveCacheHelper<ArtifactMetadata.Descriptor>
                                ): JobResult<ArchiveData<ArtifactMetadata.Descriptor, CacheableArchiveResource>, ArchiveException> {
                                    return JobResult.Failure(ArchiveException.NotSupported("In test context", trace()))

                                }

                                override suspend fun load(
                                    data: ArchiveData<ArtifactMetadata.Descriptor, CachedArchiveResource>,
                                    helper: ResolutionHelper
                                ): JobResult<T, ArchiveException> {
                                    return JobResult.Failure(ArchiveException.NotSupported("In test context", trace()))
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
                        )
                    })
                })
        }
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

public fun emptyAccessTree(it: ArtifactMetadata.Descriptor): ArchiveAccessTree =
    object : ArchiveAccessTree {
        override val descriptor: ArtifactMetadata.Descriptor = it
        override val targets: Set<ArchiveTarget> = setOf()
//        override val parents: Set<ArchiveAccessTree> = setOf()
    }