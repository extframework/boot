package net.yakclient.boot.dependency

import arrow.core.Either
import arrow.core.continuations.either
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositorySettings
import net.yakclient.boot.archive.ArchiveLoadException

public interface DependencyGraphProvider<R: ArtifactRequest<*>, S: RepositorySettings> {
    public val name: String
    public val graph: DependencyGraph<R, *, S>

    public fun parseRequest(request: Map<String, String>) : R?

    public fun parseSettings(settings: Map<String, String>): S?
}

public fun <S : RepositorySettings, R : ArtifactRequest<*>> DependencyGraphProvider<R, S>.getArtifact(
    pSettings: Map<String, String>,
    pRequest: Map<String, String>,
): Either<ArchiveLoadException, Unit> = either.eager {
    val settings = parseSettings(pSettings) ?: shift(ArchiveLoadException.DependencyInfoParseFailed)

    val request = parseRequest(pRequest) ?: shift(ArchiveLoadException.DependencyInfoParseFailed)

    val loader = graph.cacherOf(settings)

    loader.cache(request).bind()
}