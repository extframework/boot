package dev.extframework.boot.maven

import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.createContext
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.resources.Resource
import dev.extframework.archives.ArchiveHandle
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveResolutionProvider
import dev.extframework.boot.archive.ZipResolutionProvider
import dev.extframework.boot.dependency.BasicDependencyNode
import dev.extframework.boot.dependency.DependencyResolver

public open class MavenDependencyResolver(
    parentClassLoader: ClassLoader,
    resolutionProvider: ArchiveResolutionProvider<*> = ZipResolutionProvider,
    private val factory: RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRepository> = SimpleMaven,
) : DependencyResolver<SimpleMavenDescriptor, SimpleMavenArtifactRequest, BasicDependencyNode<SimpleMavenDescriptor>, SimpleMavenRepositorySettings, SimpleMavenArtifactMetadata>(
    parentClassLoader, resolutionProvider
), MavenLikeResolver<BasicDependencyNode<SimpleMavenDescriptor>, SimpleMavenArtifactMetadata> {
    override fun createContext(
        settings: SimpleMavenRepositorySettings
    ): ResolutionContext<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactMetadata> =factory.createContext(settings)

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

    override fun SimpleMavenArtifactMetadata.resource(): Resource? = resource

    override val name: String = "simple-maven"
    override val metadataType: Class<SimpleMavenArtifactMetadata> = SimpleMavenArtifactMetadata::class.java

}