package dev.extframework.boot.test

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.job
import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.archive.printTree
import dev.extframework.boot.archive.toGraphable

public fun ArchiveGraph.dump(): Job<Unit> = job {
    println(" --------------------------------------------------- ")
    println(" ----- The following archives have been loaded ----- ")
    println(" --------------------------------------------------- ")

    val visited = HashSet<String>()
    values
        .map { v ->
            v.toGraphable {
                visited.add(it.name)

                get(it)
            }
        }
        .filterNot { visited.contains(it.name) }
        .forEach {
            printTree(it)().merge()

            println(" --------------------------------------------------- ")
        }
}