package net.yakclient.boot.util

import com.durganmcbroom.jobs.JobResult
import com.durganmcbroom.jobs.JobScope
import com.durganmcbroom.jobs.jobElement
import com.durganmcbroom.jobs.jobScope
import kotlinx.coroutines.coroutineScope
import net.yakclient.boot.archive.ArchiveException
import net.yakclient.boot.archive.ArchiveTrace
import kotlin.coroutines.coroutineContext

public class FailureFilteringScope<E : Any> {
    public fun casuallyFail(reason: E): Nothing {
        throw Breakout(reason)
    }

    public fun <T> JobResult<T, E>.casuallyAttempt(): T {
        return orNull() ?: casuallyFail(failureOrNull()!!)
    }

    public data class Breakout(
        val e: Any
    ) : Throwable()
}

public inline fun <T, S, E : Any> Collection<T>.firstNotFailureOf(
    transformer: FailureFilteringScope<E>.(T) -> JobResult<S, E>
): JobResult<S, E> {
    check(isNotEmpty()) { "Collection cannot be empty!" }

    var output: JobResult<S, E>? = null

    val scope = FailureFilteringScope<E>()

    forEach {
        try {
            output = scope.transformer(it)
            if (output!!.wasSuccess()) return output!!
        } catch (e: FailureFilteringScope.Breakout) {
            if (output == null) {
                output = JobResult.Failure(e.e as E)
            }
        }
    }

    return output!!
}

public fun <T, E> Collection<JobResult<T, E>>.mapNotFailure() : JobResult<List<T>, E> {
    return JobResult.Success(map {
        it.orNull() ?: return@mapNotFailure JobResult.Failure(it.failureOrNull()!!)
    })
}

public suspend fun <T, R, E> Collection<T>.mapFailing(mapper: suspend JobScope<E>.(T) -> R) : JobResult<List<R>, E> = jobScope {
    val scope = this@jobScope
    map {
        scope.mapper(it)
    }
}

public fun <K, V> mapOfNonNullValues(
    vararg pairs: Pair<K, V?>
): Map<K, V> {
    return pairs.filterNot { it.second == null }.associate { it } as Map<K, V>
}

public suspend fun <V> Map<String, V>.requireKeyInDescriptor(key: String): V = coroutineScope {
    this@requireKeyInDescriptor[key]
        ?: throw ArchiveException.DependencyInfoParseFailed("Failed to find key: '$key' in serialized descriptor : '$this'.", jobElement(ArchiveTrace))
}