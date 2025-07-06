package com.kaolinmc.boot.archive

import com.kaolinmc.boot.audit.Auditor
import com.kaolinmc.boot.monad.Tree
import com.kaolinmc.boot.util.typeOf

public interface ArchiveTreeAuditor : Auditor<ArchiveTreeAuditContext> {
    override val type: Class<ArchiveTreeAuditContext>
        get() = typeOf()
}

public class ArchiveTreeAuditContext internal constructor(
    public val tree: Tree<TaggedIArchive>,
    public val trace: ArchiveTrace,
    public val graph: ArchiveGraph
) {
    public fun copy(
        tree: Tree<TaggedIArchive>
    ): ArchiveTreeAuditContext {
        return ArchiveTreeAuditContext(tree, trace, graph)
    }
}