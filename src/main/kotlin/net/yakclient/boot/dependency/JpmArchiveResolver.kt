package net.yakclient.boot.dependency

import com.fasterxml.jackson.databind.deser.ValueInstantiator.Delegating
import net.yakclient.archives.ArchiveHandle
import net.yakclient.archives.ArchiveReference
import net.yakclient.archives.Archives
import net.yakclient.boot.loader.*

public open class JpmArchiveResolver : ArchiveHandleMaker {
    override fun invoke(archive: ArchiveReference, dependants: Set<ArchiveHandle>): ArchiveHandle {
        fun ArchiveLoader(
            handle: ArchiveReference,
            components: List<ClassProvider>,
            parent: ClassLoader
        ) = IntegratedLoader(
            cp = DelegatingClassProvider(components),
            sp = ArchiveSourceProvider(handle),
            parent = parent
        )

        val loader = ArchiveLoader(archive, dependants.map(::ArchiveClassProvider), ClassLoader.getSystemClassLoader())

        return Archives.resolve(archive, loader, Archives.Resolvers.JPM_RESOLVER, dependants).archive
    }
}