package dev.extframework.boot.test

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.util.printTree
import dev.extframework.boot.util.toGraphable

public fun ArchiveGraph.dump(): Job<Unit> = job {
    println(" --------------------------------------------------- ")
    println(" ----- The following archives have been loaded ----- ")
    println(" --------------------------------------------------- ")

    val visited = HashSet<String>()
    nodes()
        .map { v ->
            v.toGraphable {
                visited.add(it.name)

                getNode(it)
            }
        }
        .filterNot { visited.contains(it.name) }
        .forEach {
            printTree(it)().merge()

            println(" --------------------------------------------------- ")
        }
}