import arrow.core.Either
import com.durganmcbroom.jobs.JobResult
import com.durganmcbroom.jobs.logging.simple.SimpleLoggerFactory
import com.durganmcbroom.jobs.progress.JobWeight
import com.durganmcbroom.jobs.progress.WeightedProgressTrackerFactory
import com.durganmcbroom.jobs.progress.simple.SimpleProgressNotifierFactory
import kotlinx.coroutines.*
import net.yakclient.boot.archive.ArchiveLoadException
import kotlin.coroutines.CoroutineContext

//package net.yakclient.boot
//
//import arrow.core.Either
//import com.durganmcbroom.jobs.*
//import com.durganmcbroom.jobs.coroutines.CoroutineJobContext
//import com.durganmcbroom.jobs.coroutines.CoroutineJobOrchestrator
//import com.durganmcbroom.jobs.logging.Logger
//import com.durganmcbroom.jobs.logging.LoggingContext
//import com.durganmcbroom.jobs.logging.simple.SimpleLogger
//import com.durganmcbroom.jobs.progress.*
//import com.durganmcbroom.jobs.progress.simple.SimpleNotifierStub
//import com.durganmcbroom.jobs.progress.simple.SimpleProgressJobContext
//import com.durganmcbroom.jobs.progress.simple.SimpleProgressNotifier
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import net.yakclient.boot.archive.ArchiveLoadException
//
//public typealias BootJob<T, E> = Job<BootJobContext, JobResult<T, E>>
//
//public fun bootContext(name: String) : BootJobContext {
//    val logger = SimpleLogger(name)
//    return BootJobContext(
//        name,
//        logger,
//        CoroutineScope(Dispatchers.Default),
//        WeightedProgressTracker(SimpleProgressNotifier(logger))
//    )
//}
//
//internal fun <T, E> failedJob(err: E) : BootJob<T, E> {
//    return Job {
//        JobResult.Failure(err)
//    }
//}
//
//internal fun <E> unitJob() : BootJob<Unit, E> {
//    return BootJob<Unit, E> {
//        JobResult.Success(Unit)
//    }
//}
//
//internal fun <T, E> JobResult<T, E>.bind(ref: NewJobReference<E>) : T {
//    if (wasFailure()) ref.earlyReturn(failureOrNull()!!)
//    return orNull()!!
//}
//
public fun <T> JobResult<T, Throwable>.orThrow() : T {
    if (wasFailure()) throw failureOrNull()!!
    return orNull()!!
}
//
public fun <T, E> Either<E, T>.asOutput(): JobResult<T, E> {
    return if (this.isLeft()) JobResult.Failure((this as Either.Left).value)
    else JobResult.Success((this as Either.Right).value)
}
//
//public data class BootJobContext(
//    override val name: String,
//    override val logger: Logger,
//    override val scope: CoroutineScope,
//    override val progress: ProgressTracker<SimpleNotifierStub>
//) : NamedJobContext<BootJobCompositionStub>,
//    CoroutineJobContext<BootJobCompositionStub>,
//    LoggingContext<BootJobCompositionStub>,
//    SimpleProgressJobContext<BootJobCompositionStub> {
//    override val orchestrator: JobOrchestrator<BootJobCompositionStub> = ProgressingJobOrchestrator(this,CoroutineJobOrchestrator(this))
//
//    public fun child(name: String, weight: Int = 1) : BootJobCompositionStub = BootJobCompositionStub(name, weight)
//
//    override fun compose(stub: BootJobCompositionStub): JobContext<BootJobCompositionStub> {
//        val subLogger = SimpleLogger(stub.name)
//        val notifierStub = SimpleNotifierStub(subLogger)
//        return BootJobContext(stub.name, subLogger, scope, progress.compose(notifierStub))
//    }
//}
//
//public data class BootJobCompositionStub(
//    override val name: String,
//    override val weight: Int
//) : NamedCompositionStub, ProgressingCompositionStub

public suspend fun <T> withWeight(influence: Int, block: suspend CoroutineScope.() -> T): T {
    return withContext(JobWeight(influence)) {
        block()
    }
}

public fun bootFactories() : CoroutineContext = WeightedProgressTrackerFactory() + SimpleLoggerFactory() + SimpleProgressNotifierFactory()
public inline fun <T, E> JobResult<T, E>.fix(block: (E) -> T) : T {
    if (wasFailure()) return block(failureOrNull()!!)
    return orNull()!!
}

//public suspend inline fun <T, E> bootJob(
//    name: String,
//    noinline block: suspend JobScope<E>.() -> T
//): Deferred<JobResult<T, E>> {
//    return job(bootContext(name), block)
//}