package net.yakclient.boot.plugin.artifact

import com.durganmcbroom.artifact.resolver.ArtifactReference
import com.durganmcbroom.artifact.resolver.ArtifactStub
import com.durganmcbroom.artifact.resolver.CheckedResource
import com.durganmcbroom.artifact.resolver.simple.maven.*
import net.yakclient.boot.plugin.PluginRuntimeModel

public typealias PluginArtifactReference = ArtifactReference<PluginArtifactMetadata, PluginArtifactStub>

public typealias PluginArtifactStub = ArtifactStub<PluginArtifactRequest, PluginRepositoryStub>

public typealias PluginRepositoryStub = SimpleMavenRepositoryStub

public typealias PluginDescriptor = SimpleMavenDescriptor

public typealias PluginChildInfo = SimpleMavenChildInfo

public data class PluginDependencyInfo(
    val type: String,
    val request: Map<String, String>,
    val repositorySettings: Map<String, String>,
)

public typealias PluginRepositorySettings = SimpleMavenRepositorySettings

public typealias PluginArtifactRequest = SimpleMavenArtifactRequest

public class PluginArtifactMetadata(
    desc: SimpleMavenDescriptor,
    resource: CheckedResource?,
    children: List<PluginChildInfo>,
    public val dependencies: List<PluginDependencyInfo>,
    public val runtimeModel: PluginRuntimeModel,
) : SimpleMavenArtifactMetadata(desc, resource, children)