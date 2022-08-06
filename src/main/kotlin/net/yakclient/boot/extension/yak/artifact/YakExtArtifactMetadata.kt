package net.yakclient.boot.extension.yak.artifact

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactResolutionOptions
import com.durganmcbroom.artifact.resolver.CheckedResource
import com.durganmcbroom.artifact.resolver.RepositoryReference
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import net.yakclient.boot.extension.yak.YakErm

public class YakExtArtifactMetadata(
    desc: YakExtDescriptor,
    resource: CheckedResource?,
    transitives: List<YakExtTransitiveInfo>,
    public val dependencies: List<YakExtDependencyInfo>,
    public val erm: YakErm
) : ArtifactMetadata<YakExtDescriptor, YakExtTransitiveInfo>(
    desc, resource, transitives
)

public typealias YakExtDescriptor = SimpleMavenDescriptor

public data class YakExtTransitiveInfo(
    override val desc: YakExtDescriptor,
    override val resolutionCandidates: List<RepositoryReference<*>>,
) : ArtifactMetadata.TransitiveInfo

public data class YakExtDependencyInfo(
    val desc: YakExtDescriptor,
    val resolutionCandidates: List<YakExtDependencyRef>,
)

public data class YakExtDependencyRef(
    val resolutionCandidate: RepositoryReference<*>,
    val options: YakExtArtifactResolutionOptions
)