package dev.extframework.boot.dependency

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.ArtifactRequest
import com.durganmcbroom.artifact.resolver.RepositorySettings
import dev.extframework.boot.archive.ArchiveException
import dev.extframework.boot.archive.ArchiveTrace
import dev.extframework.`object`.ObjectContainer

public interface DependencyResolverProvider<K : ArtifactMetadata.Descriptor, R : ArtifactRequest<K>, S : RepositorySettings>: ObjectContainer.IDed {
    override val id: String
    public val resolver: DependencyResolver<K, R, out DependencyNode<K>, S, *>

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