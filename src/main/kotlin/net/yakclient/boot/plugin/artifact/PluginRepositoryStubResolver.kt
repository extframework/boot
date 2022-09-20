package net.yakclient.boot.plugin.artifact

import arrow.core.Either
import arrow.core.continuations.either
import com.durganmcbroom.artifact.resolver.RepositoryStubResolutionException
import com.durganmcbroom.artifact.resolver.RepositoryStubResolver
import com.durganmcbroom.artifact.resolver.simple.maven.HashType
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenArtifactRequest
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositorySettings
import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenRepositoryStubResolver
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenDefaultLayout
import com.durganmcbroom.artifact.resolver.simple.maven.layout.SimpleMavenRepositoryLayout
import net.yakclient.boot.plugin.PluginRuntimeModelRepository

public class PluginRepositoryStubResolver(
    private val preferredHash: HashType,
) : RepositoryStubResolver<PluginRepositoryStub, PluginRepositorySettings> {
    override fun resolve(
        stub: PluginRepositoryStub,
    ): Either<RepositoryStubResolutionException, PluginRepositorySettings> = either.eager {
//        ensure(stub.unresolvedRepository.layout == PluginRuntimeModelRepository.PLUGIN_REPO_TYPE || stub.unresolvedRepository.layout == PluginRuntimeModelRepository.LOCAL_PLUGIN_REPO_TYPE) {
//            RepositoryStubResolutionException(
//                "Unknown layout: '${stub.unresolvedRepository.layout}"
//            )
//        }
        val layout =
            if (stub.unresolvedRepository.layout == PluginRuntimeModelRepository.PLUGIN_REPO_TYPE) SimpleMavenDefaultLayout(
                stub.unresolvedRepository.url,
                preferredHash,
                stub.unresolvedRepository.releases.enabled,
                stub.unresolvedRepository.snapshots.enabled
            ) else if (stub.unresolvedRepository.layout == PluginRuntimeModelRepository.LOCAL_PLUGIN_REPO_TYPE) SimpleMavenRepositorySettings.local(
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
