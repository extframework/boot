package dev.extframework.boot.audit

import kotlin.reflect.KClass

public data class Auditors(
    val auditors: Map<Class<*>, Auditor<*>> = HashMap()
) {
    public constructor(
        vararg auditors: Auditor<*>
    ) : this(auditors.associateBy { it.type })

    public operator fun <T : Any> get(cls: Class<T>): Auditor<T> =
        getOrNull(cls) ?: Auditor(cls) {
            it
        }

    public fun <T : Any> getOrNull(cls: Class<T>): Auditor<T>? = auditors[cls] as? Auditor<T>

    public fun replace(auditor: Auditor<*>): Auditors {
        val newMap = auditors.toMutableMap()
        newMap[auditor.type] = auditor

        return Auditors(newMap)
    }

    public fun <T : Any> chain(auditor: Auditor<T>): Auditors {
        return replace(getOrNull(auditor.type)?.chain(auditor) ?: auditor)
    }
}

public operator fun <T : Any> Auditors.get(
    cls: KClass<T>
): Auditor<T> = getOrNull(cls.java) ?: Auditor(cls.java) {
    it
}