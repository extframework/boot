package net.yakclient.boot.component.artifact

import com.durganmcbroom.artifact.resolver.RepositoryFactory

public object SoftwareComponentRepositoryFactory : RepositoryFactory<SoftwareComponentRepositorySettings, SoftwareComponentArtifactRequest, SoftwareComponentArtifactStub, SoftwareComponentArtifactReference, SoftwareComponentArtifactRepository> {
    override fun createNew(settings: SoftwareComponentRepositorySettings): SoftwareComponentArtifactRepository = SoftwareComponentArtifactRepository(settings, this)
}