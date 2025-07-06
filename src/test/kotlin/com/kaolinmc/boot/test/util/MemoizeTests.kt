package com.kaolinmc.boot.test.util

import com.kaolinmc.boot.util.MemoizationPool
import com.kaolinmc.boot.util.MemoizedRecursiveAsynchronousFunction
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.DurationUnit
import kotlin.time.measureTime

class MemoizeTests {
    @Test
    fun `Fib test`() {
        fun fib(n: Int): Int {
            return when (n) {
                0 -> 0
                1 -> 1
                else -> fib(n - 1) + fib(n - 2)
            }
        }

        val asyncFib = MemoizedRecursiveAsynchronousFunction<Long, Long> {
            when (it) {
                0L -> 0L
                1L -> 1L
                else -> call(it - 1) + call(it - 2)
            }
        }

        runBlocking {
//            println(measureTime {
//                fib(100)
//            }.toDouble(DurationUnit.MILLISECONDS))

            println("DONE HERE")

            val pool = MemoizationPool(asyncFib)

            println(measureTime {
                println(pool.submit(100))
            }.toDouble(DurationUnit.MILLISECONDS))

            println(measureTime {
                println(pool.submit(100))
            }.toDouble(DurationUnit.MILLISECONDS))
        }
    }
}