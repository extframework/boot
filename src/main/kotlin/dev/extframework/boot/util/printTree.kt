package dev.extframework.boot.util

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.logging.LogLevel
import com.durganmcbroom.jobs.logging.Logger
import com.durganmcbroom.jobs.logging.logger
import dev.extframework.boot.archive.ArchiveNode
import dev.extframework.boot.monad.Tree

public interface Graphable {
    public val name: String

    public val children: List<Graphable>
}

public fun Artifact<*>.toGraphable(): Graphable = object : Graphable {
    override val name: String = metadata.descriptor.name
    override val children: List<Graphable> = this@toGraphable.children.map { it.toGraphable() }
}

public fun printTree(artifact: Artifact<*>): Job<Unit> = printTree(artifact.toGraphable())

public fun ArchiveNode<*>.toGraphable(): Graphable = object : Graphable {
    override val name: String = descriptor.name
    override val children: List<Graphable> = access.targets.map {
        it.relationship.node.toGraphable()
    }
}

public fun printTree(graph: Graphable): Job<Unit> = job {
    logger.log(LogLevel.INFO, textifyTree(graph)().merge())
}

public fun textifyTree(graph: Graphable): Job<String> = job {
    val alreadyPrinted = HashSet<String>()

    val builder = StringBuilder()

    fun printTreeInternal(graph: Graphable, prefix: String, isLast: Boolean, logger: Logger) {
        val hasntSeenBefore = alreadyPrinted.add(graph.name)

        builder.append(
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

                printTreeInternal(it, newPrefix, childIsLast, logger)
            }
    }

    printTreeInternal(graph, "", true, logger)

    builder.toString()
}

public fun <T> Tree<T>.toGraphable(
    map: (T) -> String
): Graphable = object : Graphable {
    override val name: String = map(item)
    override val children: List<Graphable> = parents.map {
        it.toGraphable(map)
    }
}