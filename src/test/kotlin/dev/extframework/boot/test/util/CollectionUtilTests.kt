package dev.extframework.boot.test.util

import com.durganmcbroom.jobs.*
import dev.extframework.boot.util.firstNotFailureOf
import kotlin.test.Test


class CollectionUtilTests {
    @Test
    fun `Test failure iterating failure`() {
        val out = (0..5).toList().firstNotFailureOf {
            if (it == 3) {
                Result.failure<String>(
                    Exception("You should see this")
                )
            } else {
                casuallyFail(Exception("Because I can"))
            }
        }

        println(out)
        check(out.isFailure)
        check(out.exceptionOrNull()?.message == "You should see this")
    }

    @Test
    fun `Test failure iterating only casual failure`() {
        val out : Result<String> = (0..5).toList().firstNotFailureOf {
            casuallyFail(Exception("Because I can $it"))
        }

        println(out)
        check(out.isFailure)
        check(out.exceptionOrNull()?.message == "Because I can 0")
    }

    @Test
    fun `Test failure iterating succeeding`() {
        val out : Result<String> = (0..5).toList().firstNotFailureOf {
            if (it == 5) {
                return@firstNotFailureOf Result.success("Yay it worked!")
            }
            casuallyFail(Exception("Because I can $it"))
        }

        println(out)
        check(out.isSuccess)
        check(out.getOrNull() == "Yay it worked!")
    }
}