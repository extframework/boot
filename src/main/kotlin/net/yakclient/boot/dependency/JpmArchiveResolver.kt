package net.yakclient.boot.dependency

import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.boot.loader.ArchiveClassProvider
import net.yakclient.boot.loader.ArchiveLoader

public open class JpmArchiveResolver : ArchiveResolver {
    override fun invoke(archive: ArchiveReference, dependants: Set<ArchiveHandle>): ArchiveHandle {
        val loader = ArchiveLoader(archive, dependants.map(::ArchiveClassProvider), ClassLoader.getSystemClassLoader())

       return Archives.resolve(archive, loader, Archives.Resolvers.JPM_RESOLVER, dependants).archive
    }
}