package net.yakclient.boot.archive

import com.durganmcbroom.artifact.resolver.ArtifactMetadata

//public data class ArchiveAccessTree<T>(
//    public val descriptor: T,
//    private val children: Set<ArchiveAccessTree<T>>
//) {
//    public fun prune(child: ArchiveAccessTree<T>): ArchiveAccessTree<T> {
//        check(children.contains(child))
//
//        return this.copy(
//            children = children.filterNotTo(HashSet()) { it == child }
//        )
//    }
//}

public fun interface ArchiveAccessAuditor {
    public fun audit(tree: ArchiveAccessTree): ArchiveAccessTree
}

public fun ArchiveAccessAuditor.chain(other: ArchiveAccessAuditor): ArchiveAccessAuditor = ArchiveAccessAuditor {
    other.audit(this@chain.audit(it))
}

public fun ArchiveAccessTree.prune(target: ArchiveTarget) : ArchiveAccessTree {
    return object : ArchiveAccessTree by this@prune {
        override val targets: Set<ArchiveTarget> = this@prune.targets.filterNotTo(HashSet()) {
            it === target
        }
    }
}

//public fun <T : ArtifactMetadata.Descriptor> ArchiveAccessTree.pruneTarget(descriptor: ArtifactMetadata.Descriptor): ArchiveAccessTree<T> {
//    return object : ArchiveAccessTree {
//        override val descriptor: T by this@pruneTarget::descriptor
//        override val children: List<ArchiveAccessTree<*>> by this@pruneTarget::children
//
//        private val prunedTargets = this@pruneTarget.targets()
//            .filterNot {it == descriptor}
//
//        override fun targets(): List<ArtifactMetadata.Descriptor> {
//            return prunedTargets
//        }
//    }
//}

//internal fun createPreliminary(
//    artifact: Artifact
//): JobResult<ArchiveAccessTree<ArtifactMetadata.Descriptor>, ArchiveException> = jobScope {
//    ArchiveAccessTree(
//        artifact.metadata.descriptor,
//        artifact.children.mapTo(HashSet()) {
//            createPreliminary(
//                it.asOutput().mapFailure {
//                    ArchiveException.ArtifactResolutionException("Failed to resolve stub: '${it.request}' under artifact: '${artifact.metadata.descriptor}' when creating preliminary audit access tree. ")
//                }.attempt()
//            ).attempt()
//        }
//    )
//}

//public fun backToArtifact()