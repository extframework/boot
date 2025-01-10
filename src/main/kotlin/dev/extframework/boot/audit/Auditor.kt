package dev.extframework.boot.audit

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobScope
import com.durganmcbroom.jobs.job

public interface Auditor<T: Any> {
    public val type: Class<T>

    public fun audit(
        event: T,
    ) : Job<T>
}

public fun <T: Any> Auditor(
    type: Class<T>,
    auditor: JobScope.(T) -> T
): Auditor<T> {
    return object : Auditor<T> {
        override val type: Class<T> = type

        override fun audit(event: T): Job<T> = job {
            auditor(event)
        }
    }
}

public inline fun <reified T: Any> Auditor(
    crossinline auditor: JobScope.(T) -> T
): Auditor<T> {
    return object : Auditor<T> {
        override val type: Class<T> = T::class.java

        override fun audit(event: T): Job<T> = job {
            auditor(event)
        }
    }
}

public fun <T: Any> List<Auditor<T>>.audit(ctx: T) : Job<T> = job() {
    fold(ctx) {acc, it ->
        it.audit(acc)().merge()
    }
}