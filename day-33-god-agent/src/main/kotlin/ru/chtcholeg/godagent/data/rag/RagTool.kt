package ru.chtcholeg.godagent.data.rag

import kotlinx.serialization.json.*
import ru.chtcholeg.godagent.data.tools.AgentTool

class RagTool(private val documentIndexer: DocumentIndexer) : AgentTool {
    override val name = "rag_search"
    override val description = "Search through indexed personal documents and knowledge base"
    override val parametersDescription = """{"query": string}"""

    override suspend fun execute(argsJson: String): String {
        val args = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            return "Error: invalid args"
        }
        val query = args["query"]?.jsonPrimitive?.contentOrNull ?: return "Error: query required"
        return documentIndexer.search(query)
    }
}
