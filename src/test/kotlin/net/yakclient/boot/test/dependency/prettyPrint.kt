package net.yakclient.boot.test.dependency

import net.yakclient.boot.archive.ArchiveNode

fun ArchiveNode<*>.prettyPrint(printer: (handle: ArchiveNode<*>, depth: Int) -> Unit) = prettyPrint(printer, 0)

private fun ArchiveNode<*>.prettyPrint(printer: (handle: ArchiveNode<*>, depth: Int) -> Unit, depth: Int) {
    printer(this, depth)
    parents.forEach { it.prettyPrint(printer, depth + 1) }
}

private const val SEPARATOR_LENGTH = 50

fun separator(title: String) {
    println((0 until SEPARATOR_LENGTH).joinToString(separator = "") { "-" })
    val wrapperLength = SEPARATOR_LENGTH / 2 - 1 - (title.length + 1) / 2
    println(
        "${(0 until wrapperLength).joinToString(separator = "") { "+" }} $title ${
            (0 until wrapperLength).joinToString(separator = "") { "+" }
        }"
    )
    println((0 until SEPARATOR_LENGTH).joinToString(separator = "") { "-" })
}