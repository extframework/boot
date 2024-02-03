package net.yakclient.boot.util

import com.durganmcbroom.jobs.JobScope

public fun <E> JobScope<E>.ensure(condition: Boolean, error: () -> E) {
    if (!condition) fail(error())
}