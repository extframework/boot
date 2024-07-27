package dev.extframework.boot.archive.audit

import com.durganmcbroom.jobs.Job
import com.durganmcbroom.jobs.JobScope
import com.durganmcbroom.jobs.job
import dev.extframework.boot.archive.ArchiveNodeResolver
import dev.extframework.boot.archive.IArchive
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.util.typeOf

public interface ArchiveTreeAuditor : ArchiveAuditor<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> {
    override val type: Class<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>>
        get() = typeOf()
}

public fun ArchiveTreeAuditor(
    auditor: JobScope.(Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>, AuditContext) -> Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>
): ArchiveTreeAuditor = object : ArchiveTreeAuditor {
    override fun audit(
        event: Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>,
        context: AuditContext
    ): Job<Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>> = job {
        auditor(event, context)
    }

}