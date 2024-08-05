package dev.extframework.boot.archive

import dev.extframework.boot.audit.Auditor
import dev.extframework.boot.monad.Tagged
import dev.extframework.boot.monad.Tree
import dev.extframework.boot.util.typeOf

public interface ArchiveTreeAuditor : Auditor<ArchiveTreeAuditContext> {
    override val type: Class<ArchiveTreeAuditContext>
        get() = typeOf()
}

public class ArchiveTreeAuditContext internal constructor(
    public val tree: Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>,
    public val trace: ArchiveTrace,
    public val graph: ArchiveGraph
) {
    public fun copy(
        tree: Tree<Tagged<IArchive<*>, ArchiveNodeResolver<*, *, *, *, *>>>
    ): ArchiveTreeAuditContext {
        return ArchiveTreeAuditContext(tree, trace, graph)
    }
}