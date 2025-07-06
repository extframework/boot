package com.kaolinmc.boot.util

import com.durganmcbroom.artifact.resolver.Artifact
import com.kaolinmc.boot.archive.ArchiveNode
import com.kaolinmc.boot.monad.Tree
import java.util.logging.Logger

public interface Graphable {
    public val name: String

    public val children: List<Graphable>
}

public fun Artifact<*>.toGraphable(): Graphable = object : Graphable {
    override val name: String = metadata.descriptor.name
    override val children: List<Graphable> = this@toGraphable.parents.map { it.toGraphable() }
}

public fun printTree(artifact: Artifact<*>, logger: Logger): Unit = printTree(artifact.toGraphable(), logger)

public fun ArchiveNode<*>.toGraphable(): Graphable = object : Graphable {
    override val name: String = descriptor.name
    override val children: List<Graphable> = access.targets.map {
        it.relationship.node.toGraphable()
    }
}

public fun printTree(graph: Graphable, logger: Logger) {
    logger.info(textifyTree(graph))
}

public fun textifyTree(graph: Graphable): String {
    val alreadyPrinted = HashSet<String>()

    val builder = StringBuilder()

    fun printTreeInternal(graph: Graphable, prefix: String, isLast: Boolean) {
        val hasntSeenBefore = alreadyPrinted.add(graph.name)

        builder.appendLine(
            prefix
                    + (if (isLast) "\\---" else "+---")
                    + " "
                    + graph.name
                    + (if (!hasntSeenBefore) "***" else "")
        )

        if (hasntSeenBefore) graph.children
            .withIndex()
            .forEach { (index, it) ->
                val childIsLast: Boolean = graph.children.lastIndex == index
                val newPrefix: String = prefix + (if (isLast)
                    "    "
                else "|   ") + " "

                printTreeInternal(it, newPrefix, childIsLast)
            }
    }

    printTreeInternal(graph, "", true)

    return builder.toString()
}

public fun <T> Tree<T>.toGraphable(
    map: (T) -> String
): Graphable = object : Graphable {
    override val name: String = map(item)
    override val children: List<Graphable> = parents.map {
        it.toGraphable(map)
    }
}