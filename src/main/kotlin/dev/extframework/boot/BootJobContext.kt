import com.durganmcbroom.jobs.BasicJobFacetFactory
import com.durganmcbroom.jobs.JobName
import com.durganmcbroom.jobs.logging.LogLevel
import com.durganmcbroom.jobs.logging.Logger
import com.durganmcbroom.jobs.logging.LoggerFactory
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.LogRecord

private class BootLogger(
    val realLogger: java.util.logging.Logger,
    override val name: String = realLogger.name,
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

public fun BootLoggerFactory(): BasicJobFacetFactory<Logger> {
    val logger = BootLogger.createLogger("boot-logger")

    return object : BasicJobFacetFactory<Logger>(listOf(), {
        val name = this.context[JobName]?.name
            ?: "<anonymous job>"

        BootLogger(logger, name)
    }), LoggerFactory {}
}