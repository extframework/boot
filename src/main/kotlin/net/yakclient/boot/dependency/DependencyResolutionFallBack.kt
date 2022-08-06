package net.yakclient.boot.dependency

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ResolvedArchive


public abstract class DependencyResolutionFallBack(
    private val fallback: ArchiveResolver,
) : ArchiveResolver {
    override fun invoke(ref: ArchiveHandle, dependants: Set<ResolvedArchive>): ResolvedArchive =
        resolve(ref, dependants) ?: fallback(ref, dependants)

    public abstract fun resolve(ref: ArchiveHandle, dependants: Set<ResolvedArchive>): ResolvedArchive?
}

public fun interface DependencyResolutionBid : (ArchiveHandle, Set<ResolvedArchive>) -> ResolvedArchive?

public fun DependencyResolutionBid.orFallBackOn(fallback: ArchiveResolver) : ArchiveResolver = object: DependencyResolutionFallBack(fallback) {
    override fun resolve(ref: ArchiveHandle, dependants: Set<ResolvedArchive>): ResolvedArchive? = this@orFallBackOn(ref, dependants)
}