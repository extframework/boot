package net.yakclient.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import net.yakclient.boot.loader.ClassProvider
import net.yakclient.boot.loader.ResourceProvider

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

    public val classes: ClassProvider
    public val resources: ResourceProvider

    public data class Direct(
        override val classes: ClassProvider,
        override val resources: ResourceProvider,
    ) : ArchiveRelationship {
        override val name: String = "DIRECT"
    }

    public data class Transitive(
        override val classes: ClassProvider,
        override val resources: ResourceProvider,
    ) : ArchiveRelationship {
        override val name: String = "TRANSITIVE"
    }

//    public companion object {
//        @JvmStatic
//        public fun direct(
//            classes: ClassProvider,
//            resources: ResourceProvider
//        ): ArchiveRelationship = object : ArchiveRelationship {
//            override val name: String = "DIRECT"
//            override val classes: ClassProvider = classes
//            override val resources: ResourceProvider = resources
//        }
//
//        @JvmStatic
//        public fun transitive(
//            classes: ClassProvider,
//            resources: ResourceProvider
//        ): ArchiveRelationship = object : ArchiveRelationship {
//            override val name: String = "TRANSITIVE"
//            override val classes: ClassProvider = classes
//            override val resources: ResourceProvider = resources
//        }
//    }
}

//public enum class ArchiveRelationship {
//    DIRECT,
//    TRANSITIVE
//}