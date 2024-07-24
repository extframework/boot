package dev.extframework.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.jobs.Job

public enum class ConstraintType {
    Negotiable,
    Bound
}

// Should not implement equals or hashcode.
public interface Constraint<T : ArtifactMetadata.Descriptor> {
    public val classifier: Any
    public val negotiator: ConstraintNegotiator<T, *>
    public val type: ConstraintType

    public val descriptor: T
}

public interface ConstraintNegotiator<K : ArtifactMetadata.Descriptor, T : Constraint<K>> {
    public val constraintType: Class<T>

    public fun constrain(
        node: IArchive<K>
    ): Job<T> = constrain(
        node,
        when (node) {
            is ArchiveData<*, *> -> ConstraintType.Negotiable
            else -> ConstraintType.Bound
        }
    )

    public fun constrain(
        node: IArchive<K>,
        type: ConstraintType
    ): Job<T>

    public fun negotiate(
        constraints: List<T>
    ): Job<T>
}