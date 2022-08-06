package net.yakclient.boot.loader

import net.yakclient.archives.ArchiveHandle
import net.yakclient.common.util.runCatching

public class ArchiveClassProvider(
    private val archive: ArchiveHandle
) : ClassProvider {
    override val packages: Set<String> = run {
        val visited = HashSet<ArchiveHandle>()

        fun parentPackages(archive: ArchiveHandle): Set<String> = archive.packages + archive.parents.flatMap {
            if (visited.contains(it)) return@flatMap listOf()
            visited.add(it)

            parentPackages(it)
        }

        parentPackages(archive)
    }

    override fun findClass(name: String): Class<*>? =
        runCatching(ClassNotFoundException::class) { archive.classloader.loadClass(name) }

    override fun findClass(name: String, module: String): Class<*>? = findClass(name)
}