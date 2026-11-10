package ohi.andre.consolelauncher.managers.cron

import com.cronutils.model.Cron
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import java.time.ZonedDateTime
import java.util.Calendar

/**
 * Cron parser and next-occurrence calculator powered by the cron-utils library.
 * Supports standard UNIX 5-field cron expressions (minute, hour, day-of-month, month, day-of-week),
 * with fallback support for Quartz / custom formats.
 */
class CronExpression(val expression: String) {

    private val cron: Cron

    init {
        val trimmed = expression.trim()
        val parts = trimmed.split("\\s+".toRegex())

        cron = try {
            if (parts.size == 5) {
                // Initialize standard UNIX 5-field cron parser
                val definition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
                val parser = CronParser(definition)
                parser.parse(trimmed)
            } else if (parts.size == 6) {
                // Quartz definition (seconds, minutes, hours, day-of-month, month, day-of-week)
                val definition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)
                val parser = CronParser(definition)
                parser.parse(trimmed)
            } else {
                throw IllegalArgumentException("Cron expression must contain 5 or 6 fields. Given: '$expression'")
            }
        } catch (e: Exception) {
            // Fallback: try parsing with lenient Spring or custom definition if needed
            val definition = CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING)
            val parser = CronParser(definition)
            parser.parse(if (parts.size == 5) "0 $trimmed" else trimmed)
        }
        cron.validate()
    }

    /**
     * Calculates the next execution timestamp after [fromMillis].
     */
    fun getNextExecutionTime(fromMillis: Long = System.currentTimeMillis()): Long {
        val executionTime = ExecutionTime.forCron(cron)
        val now = ZonedDateTime.now()
        val fromTime = if (fromMillis == System.currentTimeMillis()) {
            now
        } else {
            val cal = Calendar.getInstance().apply { timeInMillis = fromMillis }
            ZonedDateTime.ofInstant(cal.toInstant(), now.zone)
        }

        val nextTimeOptional = executionTime.nextExecution(fromTime)
        if (nextTimeOptional.isPresent) {
            return nextTimeOptional.get().toInstant().toEpochMilli()
        }

        throw IllegalArgumentException("Could not calculate next execution time for cron expression: $expression")
    }

    /**
     * Calculates delay in milliseconds from [fromMillis] to the next execution window.
     */
    fun getNextDelayMillis(fromMillis: Long = System.currentTimeMillis()): Long {
        val next = getNextExecutionTime(fromMillis)
        return (next - fromMillis).coerceAtLeast(1000L)
    }
}
