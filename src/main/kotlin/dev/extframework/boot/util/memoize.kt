package dev.extframework.boot.util

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

public class MemoizationPool<I: Any, O>(
    func: MemoizedRecursiveAsynchronousFunction<I, O>
) {
    @JvmOverloads
    public constructor(
        key: (I) -> Any = { it },
        call: suspend MemoizedRecursiveAsynchronousScope<I, O>.(I) -> O
    ) : this(
        MemoizedRecursiveAsynchronousFunction(key, call)
    )

    private val scope = MemoizedRecursiveAsynchronousScopeImpl(func)

    public fun remove(key: Any) {
        scope.completed.remove(key)
    }

    public suspend fun submit(parameter: I): O = scope.call(parameter)

    public suspend operator fun invoke(parameter: I): O = submit(parameter)
}

public class MemoizedRecursiveAsynchronousFunction<I: Any, O>(
    internal val key: (I) -> Any = { it },
    internal val call: suspend MemoizedRecursiveAsynchronousScope<I, O>.(I) -> O,
)

public interface MemoizedRecursiveAsynchronousScope<I, O> {
    public suspend fun call(
        parameter: I
    ): O
}

public suspend operator fun <I: Any, O> MemoizedRecursiveAsynchronousFunction<I, O>.invoke(
    parameter: I
): O = MemoizedRecursiveAsynchronousScopeImpl(this).call(parameter)

internal class MemoizedRecursiveAsynchronousScopeImpl<I: Any, O>(
    val func: MemoizedRecursiveAsynchronousFunction<I, O>
) : MemoizedRecursiveAsynchronousScope<I, O> {
    val mutex = Mutex()
    val completed = ConcurrentHashMap<Any, O>()
    val working = ConcurrentHashMap<Any, Deferred<O>>()

    override suspend fun call(
        parameter: I
    ): O = coroutineScope {
        val key = func.key(parameter)
        completed[key] ?: working[key]?.await() ?: run {
            mutex.withSuspendingLock {
                val job = async {
                    val result = func.call(this@MemoizedRecursiveAsynchronousScopeImpl, parameter)

                    working.remove(key)
                    completed[key] = result

                    result
                }

                working[key] = job

                job
            }.await()
        }
    }
}