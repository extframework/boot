package net.yakclient.boot.maven

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.*
import net.yakclient.archives.ResolutionResult
import net.yakclient.boot.archive.ArchiveResolutionProvider
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.boot.dependency.DependencyResolver
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import kotlin.reflect.KClass

public open class MavenDependencyResolver(
    archiveResolver: ArchiveResolutionProvider<ResolutionResult>,
    privilegeManager: PrivilegeManager = PrivilegeManager(null, PrivilegeAccess.allPrivileges()) {},
    override val factory: RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactStub, SimpleMavenArtifactReference, SimpleMavenArtifactRepository> = SimpleMaven,
) : DependencyResolver<SimpleMavenDescriptor, SimpleMavenArtifactRequest, SimpleMavenRepositorySettings, SimpleMavenRepositoryStub, SimpleMavenArtifactMetadata>(
    archiveResolver, privilegeManager
), MavenLikeResolver<SimpleMavenArtifactRequest, DependencyNode, SimpleMavenRepositorySettings, SimpleMavenRepositoryStub, SimpleMavenArtifactMetadata> {
    override val name: String = "simple-maven"
    override val metadataType: KClass<SimpleMavenArtifactMetadata> = SimpleMavenArtifactMetadata::class
}