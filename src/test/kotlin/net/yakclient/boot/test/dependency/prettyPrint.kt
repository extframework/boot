package net.yakclient.boot.test.dependency

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.archive.ArchiveNode

fun ArchiveNode.prettyPrint(printer: (handle: ArchiveHandle?, depth: Int) -> Unit) = prettyPrint(printer, 0)

private fun ArchiveNode.prettyPrint(printer: (handle: ArchiveHandle?, depth: Int) -> Unit, depth: Int) {
    printer(archive, depth)
    children.forEach { it.prettyPrint(printer, depth + 1) }
}