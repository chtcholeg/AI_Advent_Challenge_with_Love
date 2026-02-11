package ru.chtcholeg.agent.data.tool

import kotlinx.serialization.json.*
import ru.chtcholeg.agent.domain.model.QuestionOption
import ru.chtcholeg.agent.domain.model.UserQuestion
import ru.chtcholeg.agent.domain.tool.LocalTool
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * AskUserQuestion tool - asks user questions with multiple choice options.
 */
class AskUserQuestionTool : LocalTool {

    override val name = "ask_user_question"

    override val description = """
        Asks the user questions during execution to gather preferences, clarify requirements, or get decisions.

        Use this tool when you need to:
        - Gather user preferences or requirements
        - Clarify ambiguous instructions
        - Get decisions on implementation choices as you work
        - Offer choices about what direction to take

        Features:
        - 1-4 questions per call
        - 2-4 options per question
        - Multi-select support for non-exclusive choices
        - Automatic "Other" option for custom input
        - Recommendation support (add "(Recommended)" to first option label)

        Plan mode note: Use this to clarify requirements BEFORE finalizing plan.
        Do NOT ask "Is my plan ready?" - use ExitPlanMode for plan approval.
    """.trimIndent()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("questions") {
                put("type", "array")
                put("description", "Questions to ask (1-4 questions)")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("question") {
                            put("type", "string")
                            put("description", "Complete question to ask (must end with '?')")
                        }
                        putJsonObject("header") {
                            put("type", "string")
                            put("description", "Short label for chip/tag (max 12 chars, e.g., 'Auth method', 'Library')")
                        }
                        putJsonObject("options") {
                            put("type", "array")
                            put("description", "Available choices (2-4 options). No 'Other' option needed - provided automatically.")
                            putJsonObject("items") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("label") {
                                        put("type", "string")
                                        put("description", "Display text (1-5 words, concise)")
                                    }
                                    putJsonObject("description") {
                                        put("type", "string")
                                        put("description", "Explanation of this option and its implications")
                                    }
                                }
                                putJsonArray("required") {
                                    add("label")
                                    add("description")
                                }
                            }
                        }
                        putJsonObject("multiSelect") {
                            put("type", "boolean")
                            put("description", "Allow multiple selections (default: false)")
                            put("default", false)
                        }
                    }
                    putJsonArray("required") {
                        add("question")
                        add("header")
                        add("options")
                    }
                }
            }
        }
        putJsonArray("required") {
            add("questions")
        }
    }

    override suspend fun execute(arguments: JsonElement): McpToolResult {
        return try {
            val args = arguments.jsonObject
            val questionsArray = args["questions"]?.jsonArray
                ?: return McpToolResult("Missing required parameter: questions", isError = true)

            if (questionsArray.size < 1 || questionsArray.size > 4) {
                return McpToolResult("Must provide 1-4 questions", isError = true)
            }

            val questions = questionsArray.map { questionJson ->
                val questionObj = questionJson.jsonObject
                val question = questionObj["question"]?.jsonPrimitive?.content
                    ?: return McpToolResult("Missing 'question' field", isError = true)

                val header = questionObj["header"]?.jsonPrimitive?.content
                    ?: return McpToolResult("Missing 'header' field", isError = true)

                if (header.length > 12) {
                    return McpToolResult("Header too long (max 12 chars): $header", isError = true)
                }

                val optionsArray = questionObj["options"]?.jsonArray
                    ?: return McpToolResult("Missing 'options' field", isError = true)

                if (optionsArray.size < 2 || optionsArray.size > 4) {
                    return McpToolResult("Must provide 2-4 options per question", isError = true)
                }

                val options = optionsArray.map { optionJson ->
                    val optionObj = optionJson.jsonObject
                    val label = optionObj["label"]?.jsonPrimitive?.content
                        ?: return McpToolResult("Missing 'label' field in option", isError = true)

                    val description = optionObj["description"]?.jsonPrimitive?.content
                        ?: return McpToolResult("Missing 'description' field in option", isError = true)

                    QuestionOption(label = label, description = description)
                }

                val multiSelect = questionObj["multiSelect"]?.jsonPrimitive?.booleanOrNull ?: false

                UserQuestion(
                    question = question,
                    header = header,
                    options = options,
                    multiSelect = multiSelect
                )
            }

            // TODO: Display questions in UI and collect user answers
            // For now, return placeholder response
            val questionsSummary = questions.joinToString("\n\n") { q ->
                buildString {
                    appendLine("Q: ${q.question}")
                    appendLine("Options:")
                    q.options.forEach { opt ->
                        appendLine("  - ${opt.label}: ${opt.description}")
                    }
                    if (q.multiSelect) {
                        appendLine("(Multiple selections allowed)")
                    }
                }
            }

            McpToolResult(
                content = "Questions prepared for user:\n\n$questionsSummary\n\n[User interaction pending - questions will be displayed in UI]",
                metadata = mapOf(
                    "type" to "user_question",
                    "question_count" to questions.size.toString()
                )
            )
        } catch (e: Exception) {
            McpToolResult("Error preparing questions: ${e.message}", isError = true)
        }
    }
}
