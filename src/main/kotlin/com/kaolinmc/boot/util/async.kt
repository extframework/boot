package com.kaolinmc.boot.util

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

public suspend fun <T, R> Collection<T>.mapAsync(transform: suspend (T) -> R): List<Deferred<R>> =
    mapAsyncTo(ArrayList(), transform)

public suspend fun <T, R, C : MutableCollection<Deferred<R>>> Collection<T>.mapAsyncTo(
    collection: C,
    transform: suspend (T) -> R
): C = coroutineScope {
    mapTo(collection) { async { transform(it) } }
}