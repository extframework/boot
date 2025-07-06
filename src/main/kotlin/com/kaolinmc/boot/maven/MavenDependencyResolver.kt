package com.kaolinmc.boot.maven

import com.durganmcbroom.artifact.resolver.ArtifactRepository
import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenRepositoryLayout
import com.durganmcbroom.resources.Resource
import com.kaolinmc.archives.ArchiveHandle
import com.kaolinmc.boot.archive.ArchiveAccessTree
import com.kaolinmc.boot.archive.ArchiveResolutionProvider
import com.kaolinmc.boot.archive.ZipResolutionProvider
import com.kaolinmc.boot.dependency.BasicDependencyNode
import com.kaolinmc.boot.dependency.DependencyResolver

public open class MavenDependencyResolver(
    parentClassLoader: ClassLoader,
    resolutionProvider: ArchiveResolutionProvider<*> = ZipResolutionProvider,
    factory: RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRepository> = SimpleMaven,
) : DependencyResolver<SimpleMavenDescriptor, SimpleMavenArtifactRequest, BasicDependencyNode<SimpleMavenDescriptor>, SimpleMavenRepositorySettings, SimpleMavenArtifactMetadata>(
    parentClassLoader, resolutionProvider
), MavenLikeResolver<BasicDependencyNode<SimpleMavenDescriptor>, SimpleMavenArtifactMetadata> {
    override fun constructNode(
        descriptor: SimpleMavenDescriptor,
        handle: ArchiveHandle?,
        parents: Set<BasicDependencyNode<SimpleMavenDescriptor>>,
        accessTree: ArchiveAccessTree
    ): BasicDependencyNode<SimpleMavenDescriptor> {
        return BasicDependencyNode(
            descriptor, handle, accessTree
        )
    }

    override suspend fun SimpleMavenArtifactMetadata.resource(): Resource? = jar()

    override val id: String = "simple-maven"
    override val metadataType: Class<SimpleMavenArtifactMetadata> = SimpleMavenArtifactMetadata::class.java
    override val apiVersion: Int = 1

    override val factory: RepositoryFactory<SimpleMavenRepositorySettings, ArtifactRepository<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactMetadata>> =
        object : RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRepository> {
            override fun createNew(settings: SimpleMavenRepositorySettings): SimpleMavenArtifactRepository {
                val delegate = factory.createNew(settings)

                return object : SimpleMavenArtifactRepository(settings, this) {
                    override val layout: SimpleMavenRepositoryLayout by delegate::layout
                    override val name: String by delegate::name

                    override suspend fun get(request: SimpleMavenArtifactRequest): SimpleMavenArtifactMetadata {
                        val metadata = delegate.get(request)

                        return SimpleMavenArtifactMetadata(
                            metadata.descriptor,
                            metadata.parents.map {
                                SimpleMavenParentInfo(
                                    it.request,
                                    it.candidates + SimpleMavenRepositorySettings.local(),
                                    it.scope
                                )
                            }
                        ) {
                            metadata.jar()
                        }
                    }
                }
            }
        }
}