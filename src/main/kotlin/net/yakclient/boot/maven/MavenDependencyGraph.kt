package net.yakclient.boot.maven

import com.durganmcbroom.artifact.resolver.ArtifactReference
import com.durganmcbroom.artifact.resolver.ArtifactStubResolver
import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.artifact.resolver.ResolutionContext
import com.durganmcbroom.artifact.resolver.simple.maven.*
import kotlinx.coroutines.coroutineScope
import net.yakclient.archives.ResolutionResult
import net.yakclient.boot.archive.ArchiveResolutionProvider
import net.yakclient.boot.dependency.DependencyData
import net.yakclient.boot.dependency.DependencyGraph
import net.yakclient.boot.dependency.DependencyNode
import net.yakclient.boot.security.PrivilegeAccess
import net.yakclient.boot.security.PrivilegeManager
import net.yakclient.boot.store.DataStore
import net.yakclient.common.util.copyTo
import net.yakclient.common.util.resolve
import net.yakclient.common.util.resource.SafeResource
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

public open class MavenDependencyGraph(
    private val path: Path,
    store: DataStore<SimpleMavenDescriptor, DependencyData<SimpleMavenDescriptor>>,
    archiveResolver: ArchiveResolutionProvider<ResolutionResult>,
    initialGraph: MutableMap<SimpleMavenDescriptor, DependencyNode> = HashMap(),
    // TODO not all privileges
    privilegeManager: PrivilegeManager = PrivilegeManager(null, PrivilegeAccess.allPrivileges()) {},
    private val stubResolutionProvider: (SimpleMavenArtifactRepository) -> ArtifactStubResolver<*, SimpleMavenArtifactStub, SimpleMavenArtifactReference> = SimpleMavenArtifactRepository::stubResolver,
    private val factory: RepositoryFactory<SimpleMavenRepositorySettings, SimpleMavenArtifactRequest, SimpleMavenArtifactStub, SimpleMavenArtifactReference, SimpleMavenArtifactRepository> = SimpleMaven
) : DependencyGraph<SimpleMavenDescriptor, SimpleMavenRepositorySettings>(
    store, factory, archiveResolver, initialGraph, privilegeManager
) {
    override fun cacherOf(settings: SimpleMavenRepositorySettings): MavenDependencyCacher {
        val artifactRepository = factory.createNew(settings)

        return MavenDependencyCacher(
            ResolutionContext(
                artifactRepository,
                stubResolutionProvider(artifactRepository),
                SimpleMaven.artifactComposer
            )
        )
    }

    override suspend fun writeResource(descriptor: SimpleMavenDescriptor, resource: SafeResource): Path =
        coroutineScope {
            val jarName = "${descriptor.artifact}-${descriptor.version}.jar"
            val jarPath = path resolve descriptor.group.replace(
                '.',
                File.separatorChar
            ) resolve descriptor.artifact resolve descriptor.version resolve jarName

            if (!Files.exists(jarPath)) resource copyTo jarPath

            jarPath
        }

    public inner class MavenDependencyCacher(
        resolver: ResolutionContext<SimpleMavenArtifactRequest, SimpleMavenArtifactStub, ArtifactReference<*, SimpleMavenArtifactStub>>,
    ) : DependencyCacher<SimpleMavenArtifactRequest, SimpleMavenArtifactStub>(resolver) {
//        override fun newLocalGraph(): LocalGraph = MavenLocalGraph()

//        private inner class MavenLocalGraph : LocalGraph() {
//            override fun getKey(request: SimpleMavenArtifactRequest): VersionIndependentDependencyKey {
//                class VersionIndependentMavenKey : VersionIndependentDependencyKey {
//                    private val group by request.descriptor::group
//                    private val artifact by request.descriptor::artifact
//                    private val classifier by request.descriptor::classifier
//
//                    override fun equals(other: Any?): Boolean {
//                        if (this === other) return true
//                        if (other !is VersionIndependentMavenKey) return false
//
//                        if (group != other.group) return false
//                        if (artifact != other.artifact) return false
//                        if (classifier != other.classifier) return false
//
//                        return true
//                    }
//
//                    override fun hashCode(): Int {
//                        var result = group.hashCode()
//                        result = 31 * result + artifact.hashCode()
//                        result = 31 * result + (classifier?.hashCode() ?: 0)
//                        return result
//                    }
//                }
//
//                return VersionIndependentMavenKey()
//            }
//        }
    }
}