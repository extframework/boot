package net.yakclient.boot.test.util

import com.durganmcbroom.jobs.JobResult
import net.yakclient.boot.util.firstNotFailureOf
import kotlin.test.Test


class CollectionUtilTests {
    @Test
    fun `Test failure iterating failure`() {
        val out = (0..5).toList().firstNotFailureOf {
            if (it == 3) {
                JobResult.Failure(
                    "You should see this"
                )
            } else {
                casuallyFail("Because I can")
            }
        }

        println(out)
        check(out.wasFailure())
        check(out.failureOrNull() == "You should see this")
    }

    @Test
    fun `Test failure iterating only casual failure`() {
        val out : JobResult<Nothing, String> = (0..5).toList().firstNotFailureOf {
            casuallyFail("Because I can $it")
        }

        println(out)
        check(out.wasFailure())
        check(out.failureOrNull() == "Because I can 0")
    }

    @Test
    fun `Test failure iterating succeeding`() {
        val out : JobResult<String, String> = (0..5).toList().firstNotFailureOf {
            if (it == 5) {
                return@firstNotFailureOf JobResult.Success("Yay it worked!")
            }
            casuallyFail("Because I can $it")
        }

        println(out)
        check(out.wasSuccess())
        check(out.orNull() == "Yay it worked!")
    }
}