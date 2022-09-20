package net.yakclient.boot.plugin.artifact

import arrow.core.Either
import arrow.core.continuations.either
import com.durganmcbroom.artifact.resolver.*

public class PluginArtifactRepository(
    settings: PluginRepositorySettings,
    override val factory: RepositoryFactory<PluginRepositorySettings, PluginArtifactRequest, PluginArtifactStub, PluginArtifactReference, ArtifactRepository<PluginArtifactRequest, PluginArtifactStub, PluginArtifactReference>>,
) : ArtifactRepository<PluginArtifactRequest, PluginArtifactStub, PluginArtifactReference> {
    override val handler: PluginMetadataHandler = PluginMetadataHandler(settings)
    override val name: String = "Boot Plugin Repository"
    override val stubResolver: ArtifactStubResolver<*, PluginArtifactStub, PluginArtifactReference> =
        PluginArtifactStubResolver(factory, settings.preferredHash)

    override fun get(
        request: PluginArtifactRequest,
    ): Either<ArtifactException, PluginArtifactReference> = either.eager {
        val metadata = handler.requestMetadata(request.descriptor).bind()

        val children = metadata.children.map {
            PluginArtifactStub(
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
