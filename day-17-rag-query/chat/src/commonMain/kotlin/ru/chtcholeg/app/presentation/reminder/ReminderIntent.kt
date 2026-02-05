package ru.chtcholeg.app.presentation.reminder

import ru.chtcholeg.app.domain.model.ReminderConfig

sealed interface ReminderIntent {
    data class Start(val config: ReminderConfig) : ReminderIntent
    data object Stop : ReminderIntent
    data class UpdateConfig(val field: String, val value: String) : ReminderIntent
    data class Activate(val config: ReminderConfig) : ReminderIntent
}
