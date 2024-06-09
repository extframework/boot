package dev.extframework.boot.dependency

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositorySettings
import com.durganmcbroom.jobs.FailingJob
import com.durganmcbroom.jobs.Job
import dev.extframework.boot.archive.ArchiveCacheHelper
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.boot.archive.ArchiveParent
import dev.extframework.boot.archive.ArchiveTrace

public interface DependencyResolverProvider<K : ArtifactMetadata.Descriptor, R : ArtifactRequest<K>, S : RepositorySettings> {
    public val name: String
    public val resolver: DependencyResolver<K, R, out DependencyNode<*>, S, *>

    public fun parseRequest(request: Map<String, String>): R?

    public fun parseSettings(settings: Map<String, String>): S?
}

public fun DependencyResolverProvider<*, *, *>.extractName(
    request: Map<String, String>,
    trace: ArchiveTrace
): Result<String> {
    return parseRequest(request)?.descriptor?.name?.let(Result.Companion::success)
        ?:  Result.failure(ArchiveException.DependencyInfoParseFailed("Failed to parse artifact request: '$request'", trace))
}

public fun <K : ArtifactMetadata.Descriptor, S : RepositorySettings, R : ArtifactRequest<K>> DependencyResolverProvider<K, R, S>.cacheArtifact(
    pSettings: Map<String, String>,
    pRequest: Map<String, String>,
    trace: ArchiveTrace,
    cacheHelper: ArchiveCacheHelper<*>
): Job<ArchiveParent<*>> {
    val settings: S = parseSettings(pSettings)
        ?: return FailingJob {
            ArchiveException.DependencyInfoParseFailed(
                "Failed to parse artifact repository settings: '$pSettings'",
                trace
            )
        }

    val request: R = parseRequest(pRequest)
        ?: return FailingJob {
            ArchiveException.DependencyInfoParseFailed(
                "Failed to parse artifact request: '$pRequest'",
                trace
            )
        }

    return cacheHelper.cache(
        request,
        settings,
        resolver
    )
}