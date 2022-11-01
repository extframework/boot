package net.yakclient.boot.component.artifact

import arrow.core.Either
import arrow.core.continuations.either
import com.durganmcbroom.artifact.resolver.RepositoryStubResolutionException
import com.durganmcbroom.artifact.resolver.RepositoryStubResolver
import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import net.yakclient.boot.component.SoftwareComponentModelRepository

public class SoftwareComponentRepositoryStubResolver(
    private val preferredHash: HashType,
) : RepositoryStubResolver<SoftwareComponentRepositoryStub, SoftwareComponentRepositorySettings> {
    override fun resolve(
        stub: SoftwareComponentRepositoryStub,
    ): Either<RepositoryStubResolutionException, SoftwareComponentRepositorySettings> = either.eager {
//        ensure(stub.unresolvedRepository.layout == PluginRuntimeModelRepository.PLUGIN_REPO_TYPE || stub.unresolvedRepository.layout == PluginRuntimeModelRepository.LOCAL_PLUGIN_REPO_TYPE) {
//            RepositoryStubResolutionException(
//                "Unknown layout: '${stub.unresolvedRepository.layout}"
//            )
//        }
        val layout =
            if (stub.unresolvedRepository.layout == SoftwareComponentModelRepository.DEFAULT) SimpleMavenDefaultLayout(
                stub.unresolvedRepository.url,
                preferredHash,
                stub.unresolvedRepository.releases.enabled,
                stub.unresolvedRepository.snapshots.enabled
            ) else if (stub.unresolvedRepository.layout == SoftwareComponentModelRepository.LOCAL) SimpleMavenRepositorySettings.local(
                stub.unresolvedRepository.url,
                preferredHash
            ).layout else shift(
                RepositoryStubResolutionException(
                    "Unknown layout: '${stub.unresolvedRepository.layout}"
                )
            )
        SimpleMavenRepositorySettings(
            layout,
            preferredHash
        )
    }
}
