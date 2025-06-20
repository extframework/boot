package dev.extframework.boot.test

import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.util.printTree
import dev.extframework.boot.util.toGraphable
import java.util.logging.Logger

public fun ArchiveGraph.dump(logger: Logger): Unit {
    println(" --------------------------------------------------- ")
    println(" ----- The following archives have been loaded ----- ")
    println(" --------------------------------------------------- ")

    val visited = HashSet<String>()
    nodes()
        .map { v ->
            v.toGraphable()
        }
        .filterNot { visited.contains(it.name) }
        .forEach {
            printTree(it, logger)

            println(" --------------------------------------------------- ")
        }
}