package net.yakclient.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactException
import com.durganmcbroom.artifact.resolver.ArtifactMetadata

public abstract class ArchiveException(
    public open val trace: ArchiveTrace
) : Exception() {
    public data class ArtifactResolutionException(val exception: ArtifactException, override val trace: ArchiveTrace) :
        ArchiveException(
            trace
        ) {
        override fun toString(): String {
            return "${exception.message} in trace: '$trace'"
        }
    }

    public data class ArchiveLoadFailed(val reason: String, override val trace: ArchiveTrace) : ArchiveException(trace)

    public class DependencyInfoParseFailed(override val message: String, trace: ArchiveTrace) :
        ArchiveException(trace) {
        override fun toString(): String = "$message in trace: $trace"
    }

    public data class ArchiveTypeNotFound(val type: String, override val trace: ArchiveTrace) : ArchiveException(trace)

    public class NotSupported(
        reason: String,
        trace: ArchiveTrace
    ) : ArchiveException(trace) {
        override val message: String = "$reason in trace $trace"
    }

    public data class ArchiveNotCached(val artifact: String, override val trace: ArchiveTrace) : ArchiveException(trace)

    public data class IllegalState(val reason: String, override val trace: ArchiveTrace) : ArchiveException(trace)

    public data class CircularArtifactException(override val trace: ArchiveTrace) : ArchiveException(trace)
}