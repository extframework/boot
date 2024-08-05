package dev.extframework.boot.archive.audit

import kotlin.reflect.KClass

public data class ArchiveAuditors(
    val auditors: Map<Class<*>, ArchiveAuditor<*>> = HashMap()
) {
    public constructor(
        vararg auditors: ArchiveAuditor<*>
    ) : this(auditors.associateBy { it.type })

    public operator fun <T : Any> get(cls: Class<T>): ArchiveAuditor<T> =
        getOrNull(cls) ?: ArchiveAuditor(cls) {
            it
        }

    public fun <T : Any> getOrNull(cls: Class<T>): ArchiveAuditor<T>? = auditors[cls] as? ArchiveAuditor<T>

    public fun replace(auditor: ArchiveAuditor<*>): ArchiveAuditors {
        val newMap = auditors.toMutableMap()
        newMap[auditor.type] = auditor

        return ArchiveAuditors(newMap)
    }

    public fun <T : Any> chain(auditor: ArchiveAuditor<T>): ArchiveAuditors {
        return replace(getOrNull(auditor.type)?.chain(auditor) ?: auditor)
    }
}

public operator fun <T : Any> ArchiveAuditors.get(
    cls: KClass<T>
): ArchiveAuditor<T> = getOrNull(cls.java) ?: ArchiveAuditor(cls.java) {
    it
}