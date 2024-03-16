package net.yakclient.boot.archive

import com.durganmcbroom.artifact.resolver.Artifact
import com.durganmcbroom.artifact.resolver.ArtifactMetadata
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.job
import com.durganmcbroom.jobs.logging.LogLevel
import com.durganmcbroom.jobs.logging.Logger
import com.durganmcbroom.jobs.logging.logger
import kotlin.coroutines.coroutineContext

internal fun printTree(artifact: Artifact<*>) =
    job(JobName("Print artifact tree for: '${artifact.metadata.descriptor}'")) {
        val alreadyPrinted = HashSet<ArtifactMetadata.Descriptor>()

        fun printTreeInternal(artifact: Artifact<*>, prefix: String, isLast: Boolean, logger: Logger) {
            val hasntSeenBefore = alreadyPrinted.add(artifact.metadata.descriptor)

            logger.log(
                LogLevel.INFO, prefix
                        + (if (isLast) "\\---" else "+---")
                        + " "
                        + artifact.metadata.descriptor.name
                        + (if (!hasntSeenBefore) "***" else "")
            )

            if (hasntSeenBefore) artifact.children
                .withIndex()
                .forEach { (index, it: Artifact<*>) ->
                    val childIsLast: Boolean = artifact.children.lastIndex == index
                    val newPrefix: String = prefix + (if (isLast)
                        "    "
                    else "|   ") + " "

                    printTreeInternal(it, newPrefix, childIsLast, logger)
                }
        }

        printTreeInternal(artifact, "", true, logger)
    }
