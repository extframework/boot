package dev.extframework.boot.constraint

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.jobs.Job
import dev.extframework.boot.archive.ArchiveRelationship

public enum class ConstraintType {
    BOUND,
    NEGOTIABLE
}

public data class Constrained<T: ArtifactMetadata.Descriptor>(
    val descriptor: T,
    val type: ConstraintType
) {
    override fun toString(): String {
        return "Constraint ($type) $descriptor"
    }
}

public interface ConstraintNegotiator<T: ArtifactMetadata.Descriptor> {
    public val descriptorType: Class<T>

    public fun classify(descriptor: T): Any

    public fun negotiate(
        constraints: List<Constrained<T>>,
    ): Job<T>
}