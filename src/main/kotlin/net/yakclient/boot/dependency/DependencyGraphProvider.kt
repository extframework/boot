package net.yakclient.boot.dependency

import arrow.core.Either
import arrow.core.continuations.either
import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositorySettings
import net.yakclient.boot.archive.ArchiveGraph
import net.yakclient.boot.archive.ArchiveLoadException

public interface DependencyGraphProvider<K: ArtifactMetadata.Descriptor, R: ArtifactRequest<K>, S: RepositorySettings> {
    public val name: String
    public val graph: DependencyGraph<K, S>

    public fun parseRequest(request: Map<String, String>) : R?

    public fun parseSettings(settings: Map<String, String>): S?
}

public fun <K: ArtifactMetadata.Descriptor, S : RepositorySettings, R : ArtifactRequest<K>> DependencyGraphProvider<K, R, S>.cacheArtifact(
    pSettings: Map<String, String>,
    pRequest: Map<String, String>,
): Either<ArchiveLoadException, Unit> = either.eager {
    val settings = parseSettings(pSettings) ?: shift(ArchiveLoadException.DependencyInfoParseFailed("Failed to parse artifact repository settings: '$pSettings'"))

    val request = parseRequest(pRequest) ?: shift(ArchiveLoadException.DependencyInfoParseFailed("Failed to parse artifact request: '$pRequest'"))

    val loader= graph.cacherOf(settings)

    (loader as DependencyGraph<*, *>.DependencyCacher<R, *>).cache(request).bind()
}