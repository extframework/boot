package dev.extframework.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactMetadata

public interface ArchiveAccessTree {
    public val descriptor: ArtifactMetadata.Descriptor

    public val targets: List<ArchiveTarget>
}

public data class ArchiveTarget(
    public val descriptor: ArtifactMetadata.Descriptor,
    public val relationship: ArchiveRelationship
) {
    override fun toString(): String {
        return relationship.name + " -> " + descriptor.name
    }
}

public interface ArchiveRelationship {
    public val name: String

    public val node: ArchiveNode<*>

    public data class Direct(
        override val node: ArchiveNode<*>
    ) : ArchiveRelationship {
        override val name: String = "DIRECT"
    }

    public data class Transitive(
        override val node: ArchiveNode<*>
    ) : ArchiveRelationship {
        override val name: String = "TRANSITIVE"
    }
}