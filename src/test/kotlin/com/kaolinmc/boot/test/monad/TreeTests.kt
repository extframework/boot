package com.kaolinmc.boot.test.monad

import com.kaolinmc.boot.monad.Tree
import com.kaolinmc.boot.monad.asSequence
import kotlin.test.Test
import kotlin.test.assertEquals

class TreeTests {
    @Test
    fun `Test tree iterator`() {
        val tree = Tree(
            "first",
            listOf(
                Tree("second", listOf()),
                Tree(
                    "third", listOf(
                        Tree("fourth", listOf()),
                    )
                )
            )
        )

        assertEquals(
            listOf("first", "second", "third", "fourth"),
            tree
                .asSequence()
                .toList(),
        )
    }
}