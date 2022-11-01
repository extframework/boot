package net.yakclient.boot.component.artifact

import arrow.core.Either
import com.durganmcbroom.artifact.resolver.*
import com.durganmcbroom.artifact.resolver.simple.maven.HashType

public class SoftwareComponentArtifactStubResolver(
    override val factory: RepositoryFactory<SoftwareComponentRepositorySettings, *, SoftwareComponentArtifactStub, SoftwareComponentArtifactReference, ArtifactRepository<SoftwareComponentArtifactRequest, SoftwareComponentArtifactStub, SoftwareComponentArtifactReference>>,
    preferredHash: HashType,
) :
    ArtifactStubResolver<SoftwareComponentRepositoryStub, SoftwareComponentArtifactStub, SoftwareComponentArtifactReference> {
    override val repositoryResolver: SoftwareComponentRepositoryStubResolver = SoftwareComponentRepositoryStubResolver(preferredHash)

    override fun resolve(stub: SoftwareComponentArtifactStub): Either<ArtifactException, SoftwareComponentArtifactReference> {
        val repos = stub.candidates
            .map(repositoryResolver::resolve)
            .mapNotNull { it.orNull() }
            .map(factory::createNew)

        return Either.fromNullable(repos.firstNotNullOfOrNull {
            it.get(stub.request).orNull()
        }).mapLeft { ArtifactException.ArtifactNotFound(stub.request.descriptor, repos.map { it.name }) }
    }
}