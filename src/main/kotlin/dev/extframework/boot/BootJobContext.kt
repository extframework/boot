import com.durganmcbroom.jobs.*
import com.durganmcbroom.jobs.logging.LogLevel
import com.durganmcbroom.jobs.logging.Logger
import com.durganmcbroom.jobs.logging.LoggerFactory
import com.durganmcbroom.jobs.progress.JobWeight
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.LogRecord
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

//package dev.extframework.boot
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
//import dev.extframework.boot.archive.ArchiveLoadException
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
//public fun <T> JobResult<T, Throwable>.orThrow(): T {
//    if (failure) throw leftOrNull()!!
//    return getOrNull()!!
//}

//
//public fun <T, E> Either<E, T>.asOutput(): JobResult<T, E> {
//    return if (this.isLeft()) F((this as Either.Left).value)
//    else JobResult.Success((this as Either.Right).value)
//}


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

//public suspend fun <T> withWeight(influence: Int, block: suspend CoroutineScope.() -> T): T {
//    return withContext(JobWeight(influence)) {
//        block()
//    }
//}

private class BootLogger(
    val realLogger: java.util.logging.Logger
) : Logger {
    companion object {
        fun createLogger(name: String): java.util.logging.Logger {
            LogManager.getLogManager().reset()
            val rootLogger: java.util.logging.Logger = LogManager.getLogManager().getLogger("")

            val value = object : Handler() {
                override fun publish(record: LogRecord) {
                    val out = when (record.level) {
                        Level.SEVERE,
                        Level.WARNING -> System.err

                        else -> System.out
                    }

                    out.println(record.message)
                }

                override fun flush() {}
                override fun close() {}
            }

            rootLogger.addHandler(value)

            return java.util.logging.Logger.getLogger(name)
        }
    }

    override val name: String by realLogger::name

    private infix fun getLevel(logger: java.util.logging.Logger): LogLevel {
        fun mapLevel(level: Level): LogLevel = when (level) {
            Level.INFO -> LogLevel.INFO
            Level.FINE -> LogLevel.DEBUG
            Level.WARNING -> LogLevel.WARNING
            Level.FINER -> LogLevel.ERROR
            Level.SEVERE -> LogLevel.CRITICAL
            else -> LogLevel.INFO
        }

        return logger.level?.let(::mapLevel) ?: getLevel(logger.parent)
    }

    override var level: LogLevel = getLevel(realLogger)
        set(value) {
            field = value
            val dmLevelToJavaLevel = dmLevelToJavaLevel(value)
            realLogger.level = dmLevelToJavaLevel
            for (h in realLogger.handlers) {
                h.level = dmLevelToJavaLevel
            }
        }

    override fun log(level: LogLevel, msg: String) {
        val l = dmLevelToJavaLevel(level)

        realLogger.log(l, msg)
    }

    private fun dmLevelToJavaLevel(level: LogLevel): Level? = when (level) {
        LogLevel.INFO -> Level.INFO
        LogLevel.DEBUG -> Level.FINE
        LogLevel.WARNING -> Level.WARNING
        LogLevel.ERROR -> Level.FINER
        LogLevel.CRITICAL -> Level.SEVERE
    }
}

private fun BootLoggerFactory() = object : BasicJobFacetFactory<Logger>(listOf(), {
    val name = this.context[JobName]?.name
        ?: "<anonymous job>"
//        ?: throw IllegalArgumentException("Cant find the job name! Make sure you add CoroutineName to the coroutine context.")

    BootLogger(BootLogger.createLogger(name))
}), LoggerFactory {}

public fun <T> runBootBlocking(
    context: JobContext = EmptyJobContext,
    block: JobScope.() -> T
): T =
    launch(context + BootLoggerFactory()) {
        block()
    }

//public inline fun <T, E> Result<T>.fix(block: (Exception) -> T): T {
//    if (failure) return block(leftOrNull()!!)
//    return getOrNull()!!
//}

//public suspend inline fun <T, E> bootJob(
//    name: String,
//    noinline block: suspend JobScope<E>.() -> T
//): Deferred<JobResult<T, E>> {
//    return job(bootContext(name), block)
//}