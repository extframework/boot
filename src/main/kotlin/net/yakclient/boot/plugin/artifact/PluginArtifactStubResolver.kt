package net.yakclient.boot.plugin.artifact

import arrow.core.Either
import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import net.yakclient.boot.plugin.PluginRuntimeModelRepository

public class PluginArtifactStubResolver(
    override val factory: RepositoryFactory<PluginRepositorySettings, *, PluginArtifactStub, PluginArtifactReference, ArtifactRepository<PluginArtifactRequest, PluginArtifactStub, PluginArtifactReference>>,
    preferredHash: HashType,
) :
    ArtifactStubResolver<PluginRepositoryStub, PluginArtifactStub, PluginArtifactReference> {
    override val repositoryResolver: PluginRepositoryStubResolver = PluginRepositoryStubResolver(preferredHash)

    override fun resolve(stub: PluginArtifactStub): Either<ArtifactException, PluginArtifactReference> {
        val repos = stub.candidates
            .map(repositoryResolver::resolve)
            .mapNotNull { it.orNull() }
            .map(factory::createNew)

        return Either.fromNullable(repos.firstNotNullOfOrNull {
            it.get(stub.request).orNull()
        }).mapLeft { ArtifactException.ArtifactNotFound(stub.request.descriptor, repos.map { it.name }) }
    }
}