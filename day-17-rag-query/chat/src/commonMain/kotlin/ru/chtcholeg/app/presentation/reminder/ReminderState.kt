package ru.chtcholeg.app.presentation.reminder

import ru.chtcholeg.app.domain.model.ReminderConfig

data class ReminderState(
    val activeConfig: ReminderConfig? = null,
    val lastSummary: String? = null,
    val lastSummaryChannel: String? = null,
    val error: String? = null
)
