package dev.extframework.boot.maven

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.*
import com.durganmcbroom.artifact.resolver.simple.maven.pom.PomRepository
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.result
import dev.extframework.archives.ArchiveHandle
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveResolutionProvider
import dev.extframework.boot.archive.ZipResolutionProvider
import dev.extframework.boot.dependency.BasicDependencyNode
import dev.extframework.boot.dependency.DependencyResolver

@Suppress("CONFLICTING_INHERITED_MEMBERS_WARNING")
public open class MavenDependencyResolver(
    parentClassLoader: ClassLoader,
    resolutionProvider: ArchiveResolutionProvider<*> = ZipResolutionProvider,
    private val factory: RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactStub, SimpleMavenArtifactReference, SimpleMavenArtifactRepository> = SimpleMaven,
) : DependencyResolver<SimpleMavenDescriptor, SimpleMavenArtifactRequest, BasicDependencyNode<SimpleMavenDescriptor>, SimpleMavenRepositorySettings, SimpleMavenArtifactMetadata>(
    parentClassLoader, resolutionProvider
), MavenLikeResolver<BasicDependencyNode<SimpleMavenDescriptor>, SimpleMavenArtifactMetadata> {
    override fun createContext(settings: SimpleMavenRepositorySettings): ResolutionContext<SimpleMavenArtifactRequest, *, SimpleMavenArtifactMetadata, *> {
        val artifactRepo = factory.createNew(settings)
        return ResolutionContext(
            artifactRepo,
            object : SimpleMavenArtifactStubResolver(object :
                RepositoryStubResolver<SimpleMavenRepositoryStub, SimpleMavenRepositorySettings> by artifactRepo.stubResolver.repositoryResolver {
                override fun resolve(stub: SimpleMavenRepositoryStub): Result<SimpleMavenRepositorySettings> =  result {
                    if (stub.unresolvedRepository.url == "local") SimpleMavenRepositorySettings.local()
                    else artifactRepo.stubResolver.repositoryResolver.resolve(stub).merge()
                }
            }, factory) {
                override fun resolve(stub: SimpleMavenArtifactStub): Job<SimpleMavenArtifactReference> {
                    return super.resolve(
                        stub.copy(
                            candidates = listOf(
                                SimpleMavenRepositoryStub(
                                    PomRepository(
                                        null, null, "local",
                                    ), false
                                )
                            ) + stub.candidates
                        )
                    )
                }
            },
            factory.artifactComposer
        )
    }

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

    override val name: String = "simple-maven"
    override val metadataType: Class<SimpleMavenArtifactMetadata> = SimpleMavenArtifactMetadata::class.java

}