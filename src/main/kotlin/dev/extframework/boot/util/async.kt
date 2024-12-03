package dev.extframework.boot.util

import kotlinx.coroutines.sync.Mutex
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
public suspend inline fun <T> Mutex.withSuspendingLock(action: suspend () -> T): T {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    lock(null)
    return try {
        action()
    } finally {
        unlock(null)
    }
}