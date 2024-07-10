package dev.extframework.boot.archive

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.logging.LogLevel
import com.durganmcbroom.jobs.logging.Logger
import com.durganmcbroom.jobs.logging.logger

public interface Graphable {
    public val name: String

    public val children: List<Graphable>
}

public fun Artifact<*>.toGraphable() : Graphable  = object: Graphable {
    override val name: String = metadata.descriptor.name
    override val children: List<Graphable> = this@toGraphable.children.map { it.toGraphable() }
}

public fun printTree(artifact: Artifact<*>): Job<Unit> = printTree(artifact.toGraphable())

public fun ArchiveNode<*>.toGraphable(
    get: (ArtifactMetadata.Descriptor) -> ArchiveNode<*>?
) : Graphable = object: Graphable {
    override val name: String = descriptor.name
    override val children: List<Graphable> = access.targets.map {
        get(it.descriptor)?.toGraphable(get) ?: object : Graphable {
            override val name: String = it.descriptor.name + " ** Not loaded"
            override val children: List<Graphable> = listOf()
        }
    }
}

public fun printTree(graph: Graphable): Job<Unit> =
    job(JobName("Print artifact tree for: '${graph.name}'")) {
        val alreadyPrinted = HashSet<String>()

        fun printTreeInternal(graph: Graphable, prefix: String, isLast: Boolean, logger: Logger) {
            val hasntSeenBefore = alreadyPrinted.add(graph.name)

            logger.log(
                LogLevel.INFO, prefix
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
    }
