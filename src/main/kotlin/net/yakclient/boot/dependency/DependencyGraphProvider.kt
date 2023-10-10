package net.yakclient.boot.dependency

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.durganmcbroom.jobs.JobResult
import com.durganmcbroom.jobs.JobResult.Success
import com.durganmcbroom.jobs.job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.coroutineScope
import net.yakclient.boot.archive.ArchiveLoadException

public interface DependencyGraphProvider<K : ArtifactMetadata.Descriptor, R : ArtifactRequest<K>, S : RepositorySettings> {
    public val name: String
    public val graph: DependencyGraph<K, S>

    public fun parseRequest(request: Map<String, String>): R?

    public fun parseSettings(settings: Map<String, String>): S?
}

public fun DependencyGraphProvider<*, *, *>.extractName(request: Map<String, String>): JobResult<String, ArchiveLoadException> {
    return parseRequest(request)?.descriptor?.name?.let(::Success)
        ?: JobResult.Failure(ArchiveLoadException.DependencyInfoParseFailed("Failed to parse artifact request: '$request'"))
}

public suspend fun <K : ArtifactMetadata.Descriptor, S : RepositorySettings, R : ArtifactRequest<K>> DependencyGraphProvider<K, R, S>.cacheArtifact(
    pSettings: Map<String, String>,
    pRequest: Map<String, String>,
): JobResult<Unit, ArchiveLoadException> = coroutineScope {
    val settings = parseSettings(pSettings)
        ?: return@coroutineScope JobResult.Failure(ArchiveLoadException.DependencyInfoParseFailed("Failed to parse artifact repository settings: '$pSettings'"))

    val request = parseRequest(pRequest)
        ?: return@coroutineScope JobResult.Failure(ArchiveLoadException.DependencyInfoParseFailed("Failed to parse artifact request: '$pRequest'"))

    val loader = graph.cacherOf(settings)
    (loader as DependencyGraph<*, *>.DependencyCacher<R, *>).cache(request)
}