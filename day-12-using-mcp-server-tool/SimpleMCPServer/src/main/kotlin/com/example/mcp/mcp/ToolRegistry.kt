package com.example.mcp.mcp

import com.example.mcp.plugins.McpPlugin
import com.example.mcp.plugins.McpTool
import org.slf4j.LoggerFactory

/**
 * Registry for managing MCP tools from plugins and built-in tools
 */
class ToolRegistry {
    private val logger = LoggerFactory.getLogger(ToolRegistry::class.java)
    private val tools = mutableMapOf<String, McpTool>()

    /**
     * Register a single tool
     */
    fun registerTool(tool: McpTool) {
        if (tools.containsKey(tool.name)) {
            logger.warn("Tool '${tool.name}' is already registered, replacing...")
        }
        tools[tool.name] = tool
        logger.info("Registered tool: ${tool.name}")
    }

    /**
     * Register all tools from a plugin
     */
    fun registerPlugin(plugin: McpPlugin) {
        logger.info("Registering tools from plugin: ${plugin.name}")
        for (tool in plugin.getTools()) {
            registerTool(tool)
        }
    }

    /**
     * Register all tools from multiple plugins
     */
    fun registerPlugins(plugins: List<McpPlugin>) {
        for (plugin in plugins) {
            registerPlugin(plugin)
        }
    }

    /**
     * Get a tool by name
     */
    fun getTool(name: String): McpTool? = tools[name]

    /**
     * Get all registered tools
     */
    fun getAllTools(): List<McpTool> = tools.values.toList()

    /**
     * Get the count of registered tools
     */
    fun toolCount(): Int = tools.size

    /**
     * Check if a tool is registered
     */
    fun hasTool(name: String): Boolean = tools.containsKey(name)

    /**
     * Unregister a tool
     */
    fun unregisterTool(name: String): Boolean {
        val removed = tools.remove(name)
        if (removed != null) {
            logger.info("Unregistered tool: $name")
        }
        return removed != null
    }

    /**
     * Clear all registered tools
     */
    fun clear() {
        tools.clear()
        logger.info("Cleared all tools from registry")
    }
}
