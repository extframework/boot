package net.yakclient.boot.test.dependency

import net.yakclient.archives.ArchiveHandle
import net.yakclient.boot.archive.ArchiveNode

fun ArchiveNode<*>.prettyPrint(printer: (handle: ArchiveNode<*>, depth: Int) -> Unit) = prettyPrint(printer, 0)

private fun ArchiveNode<*>.prettyPrint(printer: (handle: ArchiveNode<*>, depth: Int) -> Unit, depth: Int) {
    printer(this, depth)
    parents.forEach { it.prettyPrint(printer, depth + 1) }
}

fun separator() {
    println("-------------------------------------------------")
    println("+++++++++++++++++++++++++++++++++++++++++++++++++")
    println("-------------------------------------------------")
}