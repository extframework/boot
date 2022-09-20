package net.yakclient.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactException

public sealed class ArchiveLoadException : Exception() {
    public data class ArtifactLoadException(val exception: ArtifactException) : ArchiveLoadException()

    public data class ArchiveLoadFailed(val reason: String) : ArchiveLoadException()

    public object DependencyInfoParseFailed : ArchiveLoadException()

    public data class DependencyTypeNotFound(val type: String) : ArchiveLoadException()

    public object ArchiveGraphInvalidated : ArchiveLoadException()

    public object NotSupported : ArchiveLoadException()

    public object ArtifactNotCached : ArchiveLoadException()

    public data class IllegalState(val reason: String) : ArchiveLoadException()
}