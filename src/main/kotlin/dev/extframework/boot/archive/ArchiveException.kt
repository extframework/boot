package dev.extframework.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.artifact.resolver.RepositorySettings

public open class ArchiveException(
    public open val trace: ArchiveTrace,
    public override val message: String? = null,
    public override val cause: Throwable? = null,
) : Exception() {
    public data class ArchiveLoadFailed(override val cause: Throwable?, override val trace: ArchiveTrace) :
        ArchiveException(trace)

    public class DependencyInfoParseFailed(override val message: String, trace: ArchiveTrace) :
        ArchiveException(trace) {
        override fun toString(): String = "$message in trace: $trace"
    }

    public data class ArchiveTypeNotFound(val type: String, override val trace: ArchiveTrace) : ArchiveException(trace)

    public data class ArchiveNotCached(val artifact: String, override val trace: ArchiveTrace) : ArchiveException(trace)

    // If the archive artifact cannot be located
    public data class ArchiveNotFound @JvmOverloads constructor(
        override val trace: ArchiveTrace,
        val archive: ArtifactMetadata.Descriptor,
        val lookedIn: List<RepositorySettings>,
        override val cause: Throwable? = null,
    ) : ArchiveException(
        trace,
        """Failed to find the artifact: '$archive'. Looked in places: 
            |${lookedIn.joinToString(separator = "\n") { " - $it" }}
        """.trimMargin()
    )

    public data class CircularArtifactException(override val trace: ArchiveTrace) : ArchiveException(trace)

    public data class UnloadingConstrained(
        override val trace: ArchiveTrace,
        val constrainedBy: Set<ArtifactMetadata.Descriptor>
    ) : ArchiveException(trace, "Cannot unload the node: '${trace.descriptor}' because it is constrained by the following: '${constrainedBy.joinToString(", ")}'")

    override fun toString(): String {
        return "ArchiveException(message=${message ?: cause?.message} in trace: '$trace')"
    }
}