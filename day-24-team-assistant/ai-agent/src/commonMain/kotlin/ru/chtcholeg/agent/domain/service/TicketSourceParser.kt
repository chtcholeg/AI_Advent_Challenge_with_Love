package ru.chtcholeg.agent.domain.service

import ru.chtcholeg.agent.domain.model.SourceReference

/**
 * Parser for extracting ticket information from CRM tool results
 * and converting them into SourceReference objects for UI display.
 */
object TicketSourceParser {

    /**
     * Parse search_tickets results and extract ticket references.
     * Format:
     * ```
     * 1. ticket_007: Subject [status]
     *    Приоритет: medium | Категория: Integration
     *    Описание: Description text...
     *    📊 Релевантность: 8.5 (Matched terms: ...)
     * ```
     *
     * @param toolResultContent Content from search_tickets tool result
     * @param startIndex Starting index for source numbering
     * @return Map of source index -> SourceReference
     */
    fun parseSearchTicketsSources(
        toolResultContent: String,
        startIndex: Int = 1
    ): Map<Int, SourceReference> {
        val sources = mutableMapOf<Int, SourceReference>()

        println("[TicketSourceParser.parseSearchTickets] Content length: ${toolResultContent.length}")
        println("[TicketSourceParser.parseSearchTickets] First 200 chars: ${toolResultContent.take(200)}")

        // Pattern: "1. ticket_007: Subject [status]"
        val ticketLinePattern = Regex("""(\d+)\.\s+(ticket_\d+):\s+(.+?)\s+\[(\w+)]""")
        val priorityPattern = Regex("""Приоритет:\s+(\w+)\s+\|\s+Категория:\s+(.+)""")
        val descriptionPattern = Regex("""Описание:\s+(.+)""")
        val relevancePattern = Regex("""📊\s+Релевантность:\s+([\d.]+)""")

        val lines = toolResultContent.lines()
        var currentIndex = startIndex
        var i = 0

        println("[TicketSourceParser.parseSearchTickets] Processing ${lines.size} lines")

        while (i < lines.size) {
            val line = lines[i]
            val ticketMatch = ticketLinePattern.find(line)

            if (ticketMatch != null) {
                val ticketId = ticketMatch.groupValues[2]
                val subject = ticketMatch.groupValues[3]
                val status = ticketMatch.groupValues[4]

                println("[TicketSourceParser.parseSearchTickets] Found ticket: $ticketId ($subject)")

                // Look ahead for additional ticket details (next 4-5 lines)
                var priority = ""
                var category = ""
                var description = ""
                var relevanceScore = 0.0

                for (j in (i + 1) until minOf(i + 6, lines.size)) {
                    val nextLine = lines[j].trim()

                    // Priority and category
                    priorityPattern.find(nextLine)?.let { match ->
                        priority = match.groupValues[1]
                        category = match.groupValues[2]
                    }

                    // Description
                    descriptionPattern.find(nextLine)?.let { match ->
                        description = match.groupValues[1]
                    }

                    // Relevance score
                    relevancePattern.find(nextLine)?.let { match ->
                        relevanceScore = match.groupValues[1].toDoubleOrNull() ?: 0.0
                    }

                    // Stop if we hit the next ticket
                    if (ticketLinePattern.find(nextLine) != null) break
                }

                // Build citation text
                val citationText = buildString {
                    append(subject)
                    if (status.isNotEmpty()) append(" [$status]")
                    if (priority.isNotEmpty()) append("\nПриоритет: $priority")
                    if (category.isNotEmpty()) {
                        if (priority.isEmpty()) append("\nКатегория: $category")
                        else append(" | Категория: $category")
                    }
                    if (description.isNotEmpty()) append("\n$description")
                }

                // Create SourceReference with ticket as "file path"
                val sourceRef = SourceReference(
                    filePath = ticketId,
                    chunkIndex = 0,
                    totalChunks = 1,
                    similarity = (relevanceScore / 10.0).toFloat(), // Normalize score (0-10) to 0.0-1.0
                    text = citationText,
                    isUrl = false
                )

                sources[currentIndex] = sourceRef
                currentIndex++
            }

            i++
        }

        println("[TicketSourceParser.parseSearchTickets] Parsed ${sources.size} ticket sources")
        return sources
    }

    /**
     * Parse get_ticket result and extract single ticket reference.
     * Format:
     * ```
     * Тикет найден:
     * ID: ticket_007
     * Пользователь: Name (email)
     * План подписки: Pro
     * Тема: Subject
     * Описание: Description
     * Статус: in_progress
     * Приоритет: medium
     * Категория: Integration
     * ```
     *
     * @param toolResultContent Content from get_ticket tool result
     * @param startIndex Starting index for source numbering
     * @return Map with single source reference
     */
    fun parseGetTicketSource(
        toolResultContent: String,
        startIndex: Int = 1
    ): Map<Int, SourceReference> {
        // Patterns for ticket details
        val idPattern = Regex("""ID:\s+(ticket_\d+)""")
        val subjectPattern = Regex("""Тема:\s+(.+)""")
        val statusPattern = Regex("""Статус:\s+(\w+)""")
        val priorityPattern = Regex("""Приоритет:\s+(\w+)""")
        val categoryPattern = Regex("""Категория:\s+(.+)""")
        val descPattern = Regex("""Описание:\s+(.+)""")

        // Extract ticket information
        val ticketId = idPattern.find(toolResultContent)?.groupValues?.get(1)
        val subject = subjectPattern.find(toolResultContent)?.groupValues?.get(1)
        val status = statusPattern.find(toolResultContent)?.groupValues?.get(1)
        val priority = priorityPattern.find(toolResultContent)?.groupValues?.get(1)
        val category = categoryPattern.find(toolResultContent)?.groupValues?.get(1)
        val description = descPattern.find(toolResultContent)?.groupValues?.get(1)

        // Build source reference only if we have minimum required info
        if (ticketId != null && subject != null) {
            val citationText = buildString {
                append(subject)
                if (status != null) append(" [$status]")
                if (priority != null) append("\nПриоритет: $priority")
                if (category != null) {
                    if (priority == null) append("\nКатегория: $category")
                    else append(" | Категория: $category")
                }
                if (description != null) append("\n$description")
            }

            val sourceRef = SourceReference(
                filePath = ticketId,
                chunkIndex = 0,
                totalChunks = 1,
                similarity = 1.0f, // Exact ticket match (get_ticket returns specific ticket)
                text = citationText,
                isUrl = false
            )

            return mapOf(startIndex to sourceRef)
        }

        return emptyMap()
    }

    /**
     * Parse get_user_tickets result and extract ticket references.
     * Similar to search_tickets but with different format.
     */
    fun parseUserTicketsSources(
        toolResultContent: String,
        startIndex: Int = 1
    ): Map<Int, SourceReference> {
        val sources = mutableMapOf<Int, SourceReference>()

        // Pattern: "- ticket_007: Subject"
        val ticketLinePattern = Regex("""^-\s+(ticket_\d+):\s+(.+)$""", RegexOption.MULTILINE)
        val statusLinePattern = Regex("""Статус:\s+(\w+)\s+\|\s+Приоритет:\s+(\w+)\s+\|\s+Категория:\s+(.+)""")
        val descPattern = Regex("""Описание:\s+(.+)""")

        val lines = toolResultContent.lines()
        var currentIndex = startIndex
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val ticketMatch = ticketLinePattern.find(line)

            if (ticketMatch != null) {
                val ticketId = ticketMatch.groupValues[1]
                val subject = ticketMatch.groupValues[2]

                // Look ahead for status, priority, category, description
                var status = ""
                var priority = ""
                var category = ""
                var description = ""

                for (j in (i + 1) until minOf(i + 5, lines.size)) {
                    val nextLine = lines[j].trim()

                    statusLinePattern.find(nextLine)?.let { match ->
                        status = match.groupValues[1]
                        priority = match.groupValues[2]
                        category = match.groupValues[3]
                    }

                    descPattern.find(nextLine)?.let { match ->
                        description = match.groupValues[1]
                    }

                    // Stop if we hit next ticket
                    if (nextLine.startsWith("-") && ticketLinePattern.find(nextLine) != null) break
                }

                val citationText = buildString {
                    append(subject)
                    if (status.isNotEmpty()) append(" [$status]")
                    if (priority.isNotEmpty()) append("\nПриоритет: $priority")
                    if (category.isNotEmpty()) {
                        if (priority.isEmpty()) append("\nКатегория: $category")
                        else append(" | Категория: $category")
                    }
                    if (description.isNotEmpty()) append("\n$description")
                }

                val sourceRef = SourceReference(
                    filePath = ticketId,
                    chunkIndex = 0,
                    totalChunks = 1,
                    similarity = 0.9f, // High relevance (user's own tickets)
                    text = citationText,
                    isUrl = false
                )

                sources[currentIndex] = sourceRef
                currentIndex++
            }

            i++
        }

        return sources
    }
}
