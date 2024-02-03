package net.yakclient.boot.component

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.jobs.JobResult
import com.durganmcbroom.jobs.jobScope
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.BootInstance
import net.yakclient.boot.archive.*
import net.yakclient.boot.component.artifact.*
import net.yakclient.boot.dependency.*
import net.yakclient.boot.loader.*
import net.yakclient.boot.maven.MavenLikeResolver
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.util.toSafeResource
import net.yakclient.common.util.resource.ProvidedResource
import java.lang.reflect.Constructor
import java.net.URI

public open class SoftwareComponentResolver(
    private val dependencyProviders: DependencyTypeContainer,
    private val bootInstance: BootInstance,
    private val parentClassLoader: ClassLoader,
    private val parentPrivilegeManager: PrivilegeManager,
) : MavenLikeResolver<
        SoftwareComponentArtifactRequest,
        SoftwareComponentNode,
        SoftwareComponentRepositorySettings,
        SoftwareComponentArtifactMetadata> {
    override val name: String = "component"
    override val nodeType: Class<SoftwareComponentNode> = SoftwareComponentNode::class.java
    override val metadataType: Class<SoftwareComponentArtifactMetadata> = SoftwareComponentArtifactMetadata::class.java
    override val factory: RepositoryFactory<SoftwareComponentRepositorySettings, SoftwareComponentArtifactRequest, *, ArtifactReference<SoftwareComponentArtifactMetadata, *>, *> =
        SoftwareComponentRepositoryFactory

    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    override suspend fun load(
        data: ArchiveData<SoftwareComponentDescriptor, CachedArchiveResource>,
        helper: ResolutionHelper
    ): JobResult<SoftwareComponentNode, ArchiveException> = jobScope {
        val runtimeModel =
            mapper.readValue<SoftwareComponentModel>(
                (data.resources["model.json"]
                    ?: fail(
                        ArchiveException.IllegalState(
                            "Failed to find model.json in resources",
                            trace()
                        )
                    )).path.toFile()
            )

        val asyncParents = data.parents.map {
            async {
                helper.load(
                    it.descriptor, this@SoftwareComponentResolver
                )
            }
        }

        val asyncDependencies = runtimeModel.dependencies.map {
            val provider = dependencyProviders.get(it.repository.type) ?: fail(
                ArchiveException.ArchiveTypeNotFound(
                    it.repository.type, trace()
                )
            )
            val descriptor = provider.parseRequest(it.request)?.descriptor
                ?: fail(ArchiveException.DependencyInfoParseFailed(it.request.toString(), trace()))

            async {
                helper.load(
                    descriptor, provider.resolver as DependencyResolver<ArtifactMetadata.Descriptor, *, *, *, *>
                )
            }
        }

        val parents = asyncParents.awaitAll()

        val dependencies = asyncDependencies.awaitAll()

        val accessTree = helper.newAccessTree {
            for (child in parents) direct(child)
            for (dependency in dependencies) direct(dependency)
        }

        val handle = data.resources["jar.jar"]?.path?.let { path ->
            ZipResolutionProvider.resolve(
                path,
                {
                    IntegratedLoader(
                        name = data.descriptor.artifact,
                        sourceProvider = ArchiveSourceProvider(it),
                        classProvider = DelegatingClassProvider(accessTree.targets.map { target ->
                            target.relationship.classes
                        }),
                        resourceProvider = ArchiveResourceProvider(it),
                        parent = parentClassLoader
                    )
                },
                parents.mapNotNullTo(HashSet()) { it.archive }
            ).attempt().archive
//            val info = RawArchiveContainerInfo(
//                data.descriptor.artifact,
//                path,
//                accessTree,
//            )
//
//            ContainerLoader.load(
//                info,
//                ContainerLoader.createHandle(),
//                RawArchiveContainerLoader(ZipResolutionProvider, parentClassLoader),
//                RootVolume,
//                PrivilegeManager(parentPrivilegeManager, PrivilegeAccess.emptyPrivileges())
//            ).attempt()
        }

        SoftwareComponentNode(
            data.descriptor,
            handle,
            parents.toSet(),
            dependencies.toSet(),
            runtimeModel,
            handle?.let { loadFactory(it, runtimeModel) },
            accessTree,
            this@SoftwareComponentResolver
        )
    }

    private fun <T : Any> Class<T>.tryGetConstructor(vararg params: Class<*>): Constructor<T>? =
        net.yakclient.common.util.runCatching(NoSuchMethodException::class) { this.getConstructor(*params) }

    private fun loadFactory(archive: ArchiveHandle, runtimeModel: SoftwareComponentModel): ComponentFactory<*, *> {
        val loadClass = archive.classloader.loadClass(runtimeModel.entrypoint)
        return (loadClass.tryGetConstructor(BootInstance::class.java)?.newInstance(bootInstance)
            ?: loadClass.tryGetConstructor()?.newInstance()) as ComponentFactory<*, *>
    }


    override suspend fun cache(
        metadata: SoftwareComponentArtifactMetadata,
        helper: ArchiveCacheHelper<SimpleMavenDescriptor>
    ): JobResult<ArchiveData<SoftwareComponentDescriptor, CacheableArchiveResource>, ArchiveException> = jobScope {
        metadata.dependencies.forEach {
            val provider =
                dependencyProviders.get(
                    it.type,
                ) ?: fail(
                    ArchiveException.ArchiveTypeNotFound(
                        it.type,
                        trace()
                    )
                )

            provider.cacheArtifact(
                it.repositorySettings,
                it.request,
                trace(),
                helper,
            ).attempt()
        }

        helper.withResource("model.json", ProvidedResource(URI.create(metadata.resource!!.location)) {
            mapper.writeValueAsBytes(metadata.runtimeModel)
        })
        helper.withResource("jar.jar", metadata.resource!!.toSafeResource())

        helper.newData(metadata.descriptor)
    }
}