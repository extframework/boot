package dev.extframework.boot.component

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.createContext
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.streamToResource
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import dev.extframework.archives.ArchiveHandle
import dev.extframework.boot.BootInstance
import dev.extframework.boot.archive.*
import dev.extframework.boot.component.artifact.SoftwareComponentArtifactMetadata
import dev.extframework.boot.component.artifact.SoftwareComponentDescriptor
import dev.extframework.boot.component.artifact.SoftwareComponentRepositoryFactory
import dev.extframework.boot.dependency.DependencyResolver
import dev.extframework.boot.dependency.DependencyTypeContainer
import dev.extframework.boot.dependency.cacheArtifact
import dev.extframework.boot.loader.ArchiveResourceProvider
import dev.extframework.boot.loader.ArchiveSourceProvider
import dev.extframework.boot.loader.DelegatingClassProvider
import dev.extframework.boot.loader.IntegratedLoader
import dev.extframework.boot.maven.MavenLikeResolver
import java.io.ByteArrayInputStream
import java.lang.reflect.Constructor

public open class SoftwareComponentResolver(
    private val dependencyProviders: DependencyTypeContainer,
    private val bootInstance: BootInstance,
    private val parentClassLoader: ClassLoader,
) : MavenLikeResolver<
        SoftwareComponentNode,
        SoftwareComponentArtifactMetadata> {
    override val name: String = "component"
    override val nodeType: Class<SoftwareComponentNode> = SoftwareComponentNode::class.java
    override val metadataType: Class<SoftwareComponentArtifactMetadata> = SoftwareComponentArtifactMetadata::class.java

    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    override fun createContext(settings: SimpleMavenRepositorySettings): ResolutionContext<SimpleMavenArtifactRequest, *, SoftwareComponentArtifactMetadata, *> {
        return SoftwareComponentRepositoryFactory.createContext(settings)
    }

    override fun load(
        data: ArchiveData<SoftwareComponentDescriptor, CachedArchiveResource>,
        helper: ResolutionHelper
    ): Job<SoftwareComponentNode> = job {
        val runtimeModel =
            mapper.readValue<SoftwareComponentModel>(
                (data.resources["model.json"]
                    ?: throw ArchiveException(
                        trace(),
                        "Failed to find model.json in resources"
                    )).path.toFile()
            )

        val (parents, dependencies) = runBlocking {
            val asyncParents = data.parents.map {
                async {
                    helper.load(
                        it.descriptor, this@SoftwareComponentResolver
                    )
                }
            }

            val asyncDependencies = runtimeModel.dependencies.map {
                val provider =
                    dependencyProviders.get(it.repository.type) ?: throw ArchiveException.ArchiveTypeNotFound(
                        it.repository.type, trace()
                    )

                val descriptor = provider.parseRequest(it.request)?.descriptor
                    ?: throw ArchiveException.DependencyInfoParseFailed(it.request.toString(), trace())

                async {
                    helper.load(
                        descriptor, provider.resolver as DependencyResolver<ArtifactMetadata.Descriptor, *, *, *, *>
                    )
                }
            }

            asyncParents.awaitAll() to asyncDependencies.awaitAll()
        }

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
            )().merge().archive
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
        dev.extframework.common.util.runCatching(NoSuchMethodException::class) { this.getConstructor(*params) }

    private fun loadFactory(archive: ArchiveHandle, runtimeModel: SoftwareComponentModel): ComponentFactory<*, *> {
        val loadClass = archive.classloader.loadClass(runtimeModel.entrypoint)
        return (loadClass.tryGetConstructor(BootInstance::class.java)?.newInstance(bootInstance)
            ?: loadClass.tryGetConstructor()?.newInstance()) as ComponentFactory<*, *>
    }

    override fun cache(
        metadata: SoftwareComponentArtifactMetadata,
        helper: ArchiveCacheHelper<SimpleMavenDescriptor>
    ): Job<ArchiveData<SoftwareComponentDescriptor, CacheableArchiveResource>> = job {
        metadata.dependencies.forEach {
            val provider =
                dependencyProviders.get(
                    it.type,
                ) ?: throw ArchiveException.ArchiveTypeNotFound(
                    it.type,
                    trace()
                )

            provider.cacheArtifact(
                it.repositorySettings,
                it.request,
                trace(),
                helper,
            )().merge()
        }

        helper.withResource("model.json", streamToResource(metadata.resource!!.location) {
            ByteArrayInputStream(mapper.writeValueAsBytes(metadata.runtimeModel))
        })
        helper.withResource("jar.jar", metadata.resource!!)

        helper.newData(metadata.descriptor)
    }
}