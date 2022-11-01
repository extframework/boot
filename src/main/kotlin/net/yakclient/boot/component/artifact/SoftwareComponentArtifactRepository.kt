package net.yakclient.boot.component.artifact

import arrow.core.Either
import arrow.core.continuations.either
import com.durganmcbroom.artifact.resolver.*

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
    ): Either<ArtifactException, SoftwareComponentArtifactReference> = either.eager {
        val metadata = handler.requestMetadata(request.descriptor).bind()

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
