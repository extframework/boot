package dev.extframework.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.jobs.JobContext

/**
 * The current place in the hierarchy of archives.
 */
public data class ArchiveTrace(
    val descriptor: ArtifactMetadata.Descriptor,
    val parent: ArchiveTrace? = null
) : JobContext.Facet {
    override val key: JobContext.Key<ArchiveTrace> = ArchiveTrace
    public fun child(descriptor: ArtifactMetadata.Descriptor): ArchiveTrace = ArchiveTrace(descriptor, this)

    public fun isCircular(toCheck: List<ArtifactMetadata.Descriptor> = listOf()): Boolean {
        return toCheck.any { it == descriptor } || parent?.isCircular(toCheck + descriptor) == true
    }

    public fun checkCircularity() {
        if (isCircular()) throw ArchiveException.CircularArtifactException(this)
    }

    public fun toList(): List<ArtifactMetadata.Descriptor> {
        return (parent?.toList() ?: listOf()) + descriptor
    }

    override fun toString(): String {
        return toList().joinToString(separator = " -> ") { it.toString() }
    }

    public companion object : JobContext.Key<ArchiveTrace> {
        override val name: String = "Archive Trace"
    }
}