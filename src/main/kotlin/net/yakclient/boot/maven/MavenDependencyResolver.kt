package net.yakclient.boot.maven

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.*
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ResolutionResult
import net.yakclient.boot.archive.ArchiveAccessTree
import net.yakclient.boot.archive.ArchiveResolutionProvider
import net.yakclient.boot.archive.ZipResolutionProvider
import net.yakclient.boot.dependency.BasicDependencyNode
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.boot.dependency.DependencyResolver
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import kotlin.reflect.KClass

public open class MavenDependencyResolver(
    parentClassLoader: ClassLoader,
//    parentPrivilegeManager: PrivilegeManager = PrivilegeManager(null, PrivilegeAccess.emptyPrivileges()),
    resolutionProvider: ArchiveResolutionProvider<*> = ZipResolutionProvider,
    override val factory: RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactStub, SimpleMavenArtifactReference, SimpleMavenArtifactRepository> = SimpleMaven,
) : DependencyResolver<SimpleMavenDescriptor, SimpleMavenArtifactRequest, BasicDependencyNode, SimpleMavenRepositorySettings, SimpleMavenArtifactMetadata>(
    parentClassLoader, resolutionProvider
),
    MavenLikeResolver<SimpleMavenArtifactRequest, BasicDependencyNode, SimpleMavenRepositorySettings, SimpleMavenArtifactMetadata> {
    override fun constructNode(
        descriptor: SimpleMavenDescriptor,
        handle: ArchiveHandle?,
        parents: Set<BasicDependencyNode>,
        accessTree: ArchiveAccessTree
    ): BasicDependencyNode {
        return BasicDependencyNode(
            descriptor, handle, parents, accessTree, this
        )
    }

    override val name: String = "simple-maven"
    override val metadataType: Class<SimpleMavenArtifactMetadata> = SimpleMavenArtifactMetadata::class.java
}