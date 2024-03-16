package net.yakclient.boot.archive

public open class ArchiveException(
    public open val trace: ArchiveTrace,
    public override val message: String? = null,
    public override val cause: Throwable? = null,
) : Exception() {
    public data class ArtifactResolutionException(override val cause: Throwable, override val trace: ArchiveTrace) :
        ArchiveException(
            trace
        )

    public data class ArchiveLoadFailed(override val cause: Throwable?, override val trace: ArchiveTrace) :
        ArchiveException(trace)

    public class DependencyInfoParseFailed(override val message: String, trace: ArchiveTrace) :
        ArchiveException(trace) {
        override fun toString(): String = "$message in trace: $trace"
    }

    public data class ArchiveTypeNotFound(val type: String, override val trace: ArchiveTrace) : ArchiveException(trace)

    public data class ArchiveNotCached(val artifact: String, override val trace: ArchiveTrace) : ArchiveException(trace)

    public data class CircularArtifactException(override val trace: ArchiveTrace) : ArchiveException(trace)

    override fun toString(): String {
        return "${message ?: cause?.message} in trace: '$trace'"
    }
}