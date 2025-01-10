package dev.extframework.boot.constraint

import dev.extframework.boot.archive.ArchiveAccessAuditContext
import dev.extframework.boot.archive.ArchiveTreeAuditContext
import dev.extframework.boot.archive.ArchiveTreeAuditor
import dev.extframework.boot.audit.Auditors

public fun Auditors.registerConstraintNegotiator(
    negotiator: ConstraintNegotiator<*>
): Auditors {
    return registerConstraintNegotiators(listOf(negotiator))
}

public fun Auditors.registerConstraintNegotiators(
    negotiator: List<ConstraintNegotiator<*>>
): Auditors {
    val (auditors, constraintAuditors) = this[ArchiveTreeAuditContext::class]
        .partition { it is ConstraintNegotiator<*> }

    val negotiators = constraintAuditors
        .filterIsInstance<ConstraintArchiveAuditor>()
        .flatMap { it.negotiators } + negotiator

    val allAuditors = auditors + ConstraintArchiveAuditor(negotiators)

    return Auditors(this.auditors + (ArchiveTreeAuditContext::class.java to allAuditors))
}