package net.yakclient.boot.util

import com.durganmcbroom.jobs.*
import kotlinx.coroutines.coroutineScope
import net.yakclient.boot.archive.ArchiveException
import net.yakclient.boot.archive.ArchiveTrace
import kotlin.coroutines.coroutineContext

public class FailureFilteringScope {
    public fun casuallyFail(reason: Throwable): Nothing {
        throw Breakout(reason)
    }

    public fun <T> Result<T>.casuallyAttempt(): T {
        return getOrNull() ?: casuallyFail(exceptionOrNull()!!)
    }

    public data class Breakout(
        val e: Throwable
    ) : Throwable()
}

public inline fun <T, S> Collection<T>.firstNotFailureOf(
    transformer: FailureFilteringScope.(T) -> Result<S>
): Result<S> {
    check(isNotEmpty()) { "Collection cannot be empty!" }

    var output: Result<S>? = null

    val scope = FailureFilteringScope()

    forEach {
        try {
            output = scope.transformer(it)
            if (output!!.isSuccess) return output!!
        } catch (e: FailureFilteringScope.Breakout) {
            if (output == null) {
                output = Result.failure(e.e)
            }
        }
    }

    return output!!
}

public fun <T> Collection<Result<T>>.mapNotFailure() : Result<List<T>> {
    return Result.success(map {
        it.getOrNull() ?: return@mapNotFailure Result.failure(it.exceptionOrNull()!!)
    })
}

public fun <T, R> Collection<T>.mapFailing(mapper: ResultScope.(T) -> R) : Result<List<R>> = result {
    val scope = this@result
    map {
        scope.mapper(it)
    }
}

public fun <K, V> mapOfNonNullValues(
    vararg pairs: Pair<K, V?>
): Map<K, V> {
    return pairs.filterNot { it.second == null }.associate { it } as Map<K, V>
}

public fun <V> Map<String, V>.requireKeyInDescriptor(key: String, trace: () -> ArchiveTrace): V =
    this@requireKeyInDescriptor[key]
        ?: throw ArchiveException.DependencyInfoParseFailed("Failed to find key: '$key' in serialized descriptor : '$this'.", trace())
