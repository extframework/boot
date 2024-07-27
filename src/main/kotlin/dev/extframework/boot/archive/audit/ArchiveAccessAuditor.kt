package dev.extframework.boot.archive.audit

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobScope
import com.durganmcbroom.jobs.job
import dev.extframework.boot.archive.ArchiveAccessTree
import dev.extframework.boot.archive.ArchiveTarget

public interface ArchiveAccessAuditor : ArchiveAuditor<ArchiveAccessTree> {
    override val type: Class<ArchiveAccessTree>
        get() = ArchiveAccessTree::class.java

    override fun audit(event: ArchiveAccessTree, context: AuditContext): Job<ArchiveAccessTree>
}

public fun ArchiveAccessAuditor(auditor: JobScope.(ArchiveAccessTree, AuditContext) -> ArchiveAccessTree): ArchiveAccessAuditor {
    return object : ArchiveAccessAuditor {
        override fun audit(event: ArchiveAccessTree, context: AuditContext): Job<ArchiveAccessTree> = job {
            auditor(event, context)
        }
    }
}

public fun ArchiveAccessAuditor.chain(other: ArchiveAccessAuditor): ArchiveAccessAuditor =
    ArchiveAccessAuditor { it, context ->
        other.audit(this@chain.audit(it, context)().merge(), context)().merge()
    }

public fun ArchiveAccessTree.prune(target: ArchiveTarget): ArchiveAccessTree {
    return object : ArchiveAccessTree by this@prune {
        override val targets: List<ArchiveTarget> = this@prune.targets.filterNot {
            it === target
        }
    }
}