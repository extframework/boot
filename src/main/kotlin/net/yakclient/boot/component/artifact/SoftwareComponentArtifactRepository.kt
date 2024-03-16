package net.yakclient.boot.component.artifact

import com.durganmcbroom.artifact.resolver.ArtifactReference
import com.durganmcbroom.artifact.resolver.ArtifactRepository
import com.durganmcbroom.artifact.resolver.ArtifactStubResolver
import com.durganmcbroom.artifact.resolver.RepositoryFactory
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job

public class SoftwareComponentArtifactRepository(
    settings: SoftwareComponentRepositorySettings,
    override val factory: RepositoryFactory<SoftwareComponentRepositorySettings, SoftwareComponentArtifactRequest, SoftwareComponentArtifactStub, SoftwareComponentArtifactReference, ArtifactRepository<SoftwareComponentArtifactRequest, SoftwareComponentArtifactStub, SoftwareComponentArtifactReference>>,
) : ArtifactRepository<SoftwareComponentArtifactRequest, SoftwareComponentArtifactStub, SoftwareComponentArtifactReference> {
    override val handler: SoftwareComponentMetadataHandler = SoftwareComponentMetadataHandler(settings)
    override val name: String = "Boot Plugin Repository"
    override val stubResolver: ArtifactStubResolver<*, SoftwareComponentArtifactStub, SoftwareComponentArtifactReference> =
        SoftwareComponentArtifactStubResolver(factory, settings.preferredHash)

    override fun get(
        request: SoftwareComponentArtifactRequest,
    ): Job<SoftwareComponentArtifactReference> = job {
        val metadata = handler.requestMetadata(request.descriptor)().merge()

        val children = metadata.children.map {
            SoftwareComponentArtifactStub(
                request.withNewDescriptor(it.descriptor),
                it.candidates
            )
        }

        ArtifactReference(
            metadata,
            children
        )
    }
}
