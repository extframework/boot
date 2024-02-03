package net.yakclient.boot.dependency

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.durganmcbroom.jobs.JobResult
import com.durganmcbroom.jobs.JobResult.Success
import kotlinx.coroutines.coroutineScope
import net.yakclient.boot.archive.*

public interface DependencyResolverProvider<K : ArtifactMetadata.Descriptor, R : ArtifactRequest<K>, S : RepositorySettings> {
    public val name: String
    public val resolver: DependencyResolver<K, R, out DependencyNode<*>, S, *>

    public fun parseRequest(request: Map<String, String>): R?

    public fun parseSettings(settings: Map<String, String>): S?
}

public fun DependencyResolverProvider<*, *, *>.extractName(request: Map<String, String>, trace: ArchiveTrace): JobResult<String, ArchiveException> {
    return parseRequest(request)?.descriptor?.name?.let(::Success)
        ?: JobResult.Failure(ArchiveException.DependencyInfoParseFailed("Failed to parse artifact request: '$request'", trace))
}

public suspend fun <K: ArtifactMetadata.Descriptor, S : RepositorySettings, R : ArtifactRequest<K>> DependencyResolverProvider<K, R, S>.cacheArtifact(
    pSettings: Map<String, String>,
    pRequest: Map<String, String>,
    trace: ArchiveTrace,
    cacheHelper: ArchiveCacheHelper<*>
): JobResult<ArchiveParent<*>, ArchiveException> = coroutineScope {
    val settings: S = parseSettings(pSettings)
        ?: return@coroutineScope JobResult.Failure(ArchiveException.DependencyInfoParseFailed("Failed to parse artifact repository settings: '$pSettings'", trace))

    val request: R = parseRequest(pRequest)
        ?: return@coroutineScope JobResult.Failure(ArchiveException.DependencyInfoParseFailed("Failed to parse artifact request: '$pRequest'", trace))

    cacheHelper.cache(
        request,
        settings,
        resolver
    )
}