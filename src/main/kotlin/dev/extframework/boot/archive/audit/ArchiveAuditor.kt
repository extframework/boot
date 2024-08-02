package dev.extframework.boot.archive.audit

import com.durganmcbroom.jobs.Job

public interface ArchiveAuditor<T> {
    public val type: Class<T>

    public fun audit(
        event: T,
        context: AuditContext
    ) : Job<T>
}