package net.yakclient.boot.component.artifact

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactReference
import com.durganmcbroom.artifact.resolver.ArtifactStub
import com.durganmcbroom.artifact.resolver.CheckedResource
import com.durganmcbroom.artifact.resolver.simple.maven.*
import net.yakclient.boot.component.SoftwareComponentModel

public typealias SoftwareComponentArtifactReference = ArtifactReference<SoftwareComponentArtifactMetadata, SoftwareComponentArtifactStub>

public typealias SoftwareComponentArtifactStub = ArtifactStub<SoftwareComponentArtifactRequest, SoftwareComponentRepositoryStub>

public typealias SoftwareComponentRepositoryStub = SimpleMavenRepositoryStub

public typealias SoftwareComponentDescriptor = SimpleMavenDescriptor

public typealias SoftwareComponentChildInfo = SimpleMavenChildInfo

//public class SoftwareComponentArtifactRequestomponentChildInfo(
//    descriptor: SoftwareComponentDescriptor,
//    candidates: List<SimpleMavenRepositoryStub>
//) : SimpleMavenChildInfo(
//    descriptor, candidates
//)


public data class SoftwareComponentDependencyInfo(
    val type: String,
    val request: Map<String, String>,
    val repositorySettings: Map<String, String>,
)

public typealias SoftwareComponentRepositorySettings = SimpleMavenRepositorySettings

public typealias SoftwareComponentArtifactRequest = SimpleMavenArtifactRequest

public class SoftwareComponentArtifactMetadata(
    desc: SimpleMavenDescriptor,
    resource: CheckedResource?,
    children: List<SoftwareComponentChildInfo>,
    public val dependencies: List<SoftwareComponentDependencyInfo>,
    public val runtimeModel: SoftwareComponentModel,
) : SimpleMavenArtifactMetadata(desc, resource, children)