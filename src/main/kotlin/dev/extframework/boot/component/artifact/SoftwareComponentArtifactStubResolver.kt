package dev.extframework.boot.component.artifact

import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.resources.ResourceAlgorithm

public class SoftwareComponentArtifactStubResolver(
    override val factory: RepositoryFactory<SoftwareComponentRepositorySettings, *, SoftwareComponentArtifactStub, SoftwareComponentArtifactReference, ArtifactRepository<SoftwareComponentArtifactRequest, SoftwareComponentArtifactStub, SoftwareComponentArtifactReference>>,
    preferredHash: ResourceAlgorithm,
) : ArtifactStubResolver<SoftwareComponentRepositoryStub, SoftwareComponentArtifactStub, SoftwareComponentArtifactReference> {
    override val repositoryResolver: SoftwareComponentRepositoryStubResolver =
        SoftwareComponentRepositoryStubResolver(preferredHash)

    override fun resolve(stub: SoftwareComponentArtifactStub): Job<SoftwareComponentArtifactReference> =
        job {
            val repos = stub.candidates
                .map(repositoryResolver::resolve)
                .map { it.merge() }
                .map(factory::createNew)

            repos.firstNotNullOfOrNull {
                it.get(stub.request)().merge()
            } ?: throw  MetadataRequestException.MetadataNotFound(stub.request.descriptor, "component-model.json")
        }
}