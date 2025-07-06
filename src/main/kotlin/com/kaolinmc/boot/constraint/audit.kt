package com.kaolinmc.boot.constraint

import com.kaolinmc.boot.archive.ArchiveTreeAuditContext
import com.kaolinmc.boot.audit.Auditors

public fun Auditors.registerConstraintNegotiator(
    negotiator: ConstraintNegotiator<*>
): Auditors {
    return registerConstraintNegotiators(listOf(negotiator))
}

public fun Auditors.registerConstraintNegotiators(
    negotiator: List<ConstraintNegotiator<*>>
): Auditors {
    val (constraintAuditors, auditors) = this[ArchiveTreeAuditContext::class]
        .partition { it is ConstraintNegotiator<*> }

    val negotiators = constraintAuditors
        .filterIsInstance<ConstraintArchiveAuditor>()
        .flatMap { it.negotiators } + negotiator

    val allAuditors = auditors + ConstraintArchiveAuditor(negotiators)

    return Auditors(this.auditors + (ArchiveTreeAuditContext::class.java to allAuditors))
}