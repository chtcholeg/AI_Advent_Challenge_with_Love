package ru.chtcholeg.app.domain.model

enum class ReminderInterval(
    val displayName: String,
    val seconds: Int
) {
    TEN_SECONDS("10 секунд", 10),
    THIRTY_SECONDS("30 секунд", 30),
    ONE_MINUTE("1 минута", 60),
    FIVE_MINUTES("5 минут", 300),
    TEN_MINUTES("10 минут", 600),
    THIRTY_MINUTES("30 минут", 1800),
    ONE_HOUR("1 час", 3600);

    companion object {
        fun fromSeconds(seconds: Int): ReminderInterval =
            entries.minByOrNull { kotlin.math.abs(it.seconds - seconds) } ?: THIRTY_SECONDS
    }
}

data class ReminderConfig(
    val id: String = "default",
    val channel: String = "",
    val interval: ReminderInterval = ReminderInterval.THIRTY_SECONDS,
    val messageCount: Int = 10,
    val enabled: Boolean = false,
    val sessionId: String? = null,
    val lastSeenMessageId: String? = null,
    val lastTriggeredAt: Long? = null,
    val createdAt: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
)
