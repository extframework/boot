package dev.extframework.boot.archive.audit

public data class ArchiveAuditors(
    val accessAuditor: ArchiveAccessAuditor = ArchiveAccessAuditor { it, _ -> it },
    val archiveTreeAuditor: ArchiveTreeAuditor = ArchiveTreeAuditor { it, _ -> it },
) {
    public fun with(
        accessAuditor: ArchiveAccessAuditor? = null,
        archiveTreeAuditor: ArchiveTreeAuditor? = null,
    ): ArchiveAuditors = ArchiveAuditors(
        accessAuditor ?: this.accessAuditor,
        archiveTreeAuditor ?: this.archiveTreeAuditor,
    )
}