package net.dungeonhub.transcripts

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import java.time.Instant
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

/**
 * Formats the timestamps shown in a transcript, in the time zone of the system generating it.
 */
@OptIn(ExperimentalTime::class)
internal object TimeFormatter {
    private val DATE = LocalDate.Format {
        day(Padding.NONE); char(' '); monthName(MonthNames.ENGLISH_FULL); char(' '); year()
    }
    private val TIME = LocalDateTime.Format {
        hour(); char(':'); minute()
    }
    private val TIME_WITH_SECONDS = LocalDateTime.Format {
        hour(); char(':'); minute(); char(':'); second()
    }
    private val SHORT_DATE = LocalDate.Format {
        day(); char('/'); monthNumber(); char('/'); year()
    }
    private val DATE_TIME = LocalDateTime.Format {
        date(DATE); char(' '); hour(); char(':'); minute()
    }
    private val FULL_DATE_TIME = LocalDateTime.Format {
        dayOfWeek(DayOfWeekNames.ENGLISH_FULL); chars(", ")
        date(DATE); chars(" at "); hour(); char(':'); minute()
    }

    fun toLocalDateTime(instant: Instant): LocalDateTime {
        return instant.toKotlinInstant().toLocalDateTime(TimeZone.currentSystemDefault())
    }

    /** e.g. `30 July 2026` */
    fun formatDate(date: LocalDate): String = date.format(DATE)

    /** e.g. `14:05` */
    fun formatTime(instant: Instant): String = toLocalDateTime(instant).format(TIME)

    /** e.g. `14:05:09` */
    fun formatTimeWithSeconds(instant: Instant): String = toLocalDateTime(instant).format(TIME_WITH_SECONDS)

    /** e.g. `Thursday, 30 July 2026 at 14:05` */
    fun formatFullDateTime(instant: Instant): String = toLocalDateTime(instant).format(FULL_DATE_TIME)

    /**
     * Formats a Discord timestamp (`<t:seconds:style>`) the way Discord displays the given style.
     * Relative timestamps (`R`) would depend on when the transcript is opened, so they're shown as a full date.
     */
    fun formatDiscordTimestamp(instant: Instant, style: Char?): String {
        val dateTime = toLocalDateTime(instant)
        return when (style) {
            't' -> dateTime.format(TIME)
            'T' -> dateTime.format(TIME_WITH_SECONDS)
            'd' -> dateTime.date.format(SHORT_DATE)
            'D' -> dateTime.date.format(DATE)
            'F', 'R' -> dateTime.format(FULL_DATE_TIME)
            else -> dateTime.format(DATE_TIME)
        }
    }
}
