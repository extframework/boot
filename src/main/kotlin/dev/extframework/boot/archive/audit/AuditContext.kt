package dev.extframework.boot.archive.audit

import dev.extframework.boot.archive.ArchiveGraph
import dev.extframework.boot.archive.ArchiveTrace

public interface AuditContext {
    public val trace: ArchiveTrace
    public val graph: ArchiveGraph
}