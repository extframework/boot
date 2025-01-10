package dev.extframework.boot.audit

import kotlin.reflect.KClass

public data class Auditors(
    val auditors: Map<Class<*>, List<Auditor<*>>> = HashMap()
) {
    public constructor(
        vararg auditors: Auditor<*>
    ) : this(auditors.groupBy { it.type })

    public operator fun <T : Any> get(cls: Class<T>): List<Auditor<T>> =
        getOrNull(cls) ?: listOf()

    public operator fun <T : Any> get(
        cls: KClass<T>
    ): List<Auditor<T>> = get(cls.java)

    public fun <T : Any> getOrNull(cls: Class<T>): List<Auditor<T>>? = auditors[cls] as? List<Auditor<T>>

//    public fun replace(auditor: Auditor<*>): Auditors {
//        val newMap = auditors.toMutableMap()
//        newMap[auditor.type] = listOf(auditor)
//
//        return Auditors(newMap)
//    }

    public fun <T : Any> chain(auditor: Auditor<T>): Auditors {
        return Auditors(auditors + (auditor.type to get(auditor.type).toMutableList().also {
            it.add(auditor)
        }))
    }

    public fun <T : Any> chain(auditor: List<Auditor<T>>): Auditors {
        if (auditor.isEmpty()) return this

        val type = auditor.first().type
        return Auditors(auditors + (type to get(type).toMutableList().also {
            it.addAll(auditor)
        }))
    }

    public fun chainAll(auditors: Auditors): Auditors {
        return auditors.auditors.values.fold(this) { acc, it ->
            acc.chain(it as List<Auditor<Any>>)
        }
    }
}

