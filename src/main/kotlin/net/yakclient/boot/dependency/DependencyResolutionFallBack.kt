package net.yakclient.boot.dependency

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference


//public abstract class DependencyResolutionFallBack(
//    private val fallback: ArchiveHandleMaker,
//) : ArchiveHandleMaker {
//    override fun invoke(ref: ArchiveReference, dependants: Set<ArchiveHandle>): ArchiveHandle =
//        resolve(ref, dependants) ?: fallback(ref, dependants)
//
//    public abstract fun resolve(ref: ArchiveReference, dependants: Set<ArchiveHandle>): ArchiveHandle?
//}
//
//public fun interface DependencyResolutionBid : (ArchiveReference, Set<ArchiveHandle>) -> ArchiveHandle?
//
//public fun DependencyResolutionBid.orFallBackOn(fallback: ArchiveHandleMaker) : ArchiveHandleMaker = object: DependencyResolutionFallBack(fallback) {
//    override fun resolve(ref: ArchiveReference, dependants: Set<ArchiveHandle>): ArchiveHandle? = this@orFallBackOn(ref, dependants)
//}