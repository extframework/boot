package dev.extframework.boot.archive

import com.durganmcbroom.jobs.Job
import dev.extframework.boot.audit.Auditor
import dev.extframework.boot.util.typeOf

public interface ArchiveAccessAuditor : Auditor<ArchiveAccessAuditContext> {
    override val type: Class<ArchiveAccessAuditContext>
        get() = typeOf()

    override fun audit(event: ArchiveAccessAuditContext): Job<ArchiveAccessAuditContext>
}

public class ArchiveAccessAuditContext internal constructor(
    public val tree: ArchiveAccessTree,
    public val trace: ArchiveTrace,
    public val graph: ArchiveGraph
) {
    public fun copy(
        tree: ArchiveAccessTree
    ): ArchiveAccessAuditContext {
        return ArchiveAccessAuditContext(tree, trace, graph)
    }
}

public fun ArchiveAccessTree.prune(target: ArchiveTarget): ArchiveAccessTree {
    return object : ArchiveAccessTree by this@prune {
        override val targets: List<ArchiveTarget> = this@prune.targets.filterNot {
            it === target
        }
    }
}