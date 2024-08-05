package dev.extframework.boot.archive.audit

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobScope
import com.durganmcbroom.jobs.job

public interface ArchiveAuditor<T: Any> {
    public val type: Class<T>

    public fun audit(
        event: T,
    ) : Job<T>
}

public fun <T: Any> ArchiveAuditor(
    type: Class<T>,
    auditor: JobScope.(T) -> T
): ArchiveAuditor<T> {
    return object : ArchiveAuditor<T> {
        override val type: Class<T> = type

        override fun audit(event: T): Job<T> = job {
            auditor(event)
        }
    }
}

public inline fun <reified T: Any> ArchiveAuditor(
    crossinline auditor: JobScope.(T) -> T
): ArchiveAuditor<T> {
    return object : ArchiveAuditor<T> {
        override val type: Class<T> = T::class.java

        override fun audit(event: T): Job<T> = job {
            auditor(event)
        }
    }
}

public fun <T: Any> ArchiveAuditor<T>.chain(other: ArchiveAuditor<T>): ArchiveAuditor<T> =
    ArchiveAuditor(type) {
        other.audit(this@chain.audit(it)().merge())().merge()
    }