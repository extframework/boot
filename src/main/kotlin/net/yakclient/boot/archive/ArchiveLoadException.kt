package net.yakclient.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactException
import com.durganmcbroom.artifact.resolver.ArtifactMetadata

public sealed class ArchiveLoadException : Exception() {
    public data class ArtifactLoadException(val exception: ArtifactException) : ArchiveLoadException() {
        override fun toString(): String {
            return exception.message
        }
    }

    public data class ArchiveLoadFailed(val reason: String) : ArchiveLoadException()

    public class DependencyInfoParseFailed(override val message: String) : ArchiveLoadException() {
        override fun toString(): String = message
    }

    public data class DependencyTypeNotFound(val type: String) : ArchiveLoadException()

    public object ArchiveGraphInvalidated : ArchiveLoadException()

    public object NotSupported : ArchiveLoadException()

    public object ArtifactNotCached : ArchiveLoadException()

    public data class IllegalState(val reason: String) : ArchiveLoadException()

    public data class CircularArtifactException(val trace: List<ArtifactMetadata.Descriptor>) : ArchiveLoadException()
}