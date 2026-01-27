package com.example.mcp.plugins

import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader

/**
 * Loads MCP plugins from JAR files in the specified directory
 */
class PluginLoader(private val pluginsDirectory: String) {
    private val logger = LoggerFactory.getLogger(PluginLoader::class.java)
    private val loadedPlugins = mutableListOf<McpPlugin>()

    fun loadPlugins(): List<McpPlugin> {
        val pluginsDir = File(pluginsDirectory)

        if (!pluginsDir.exists()) {
            logger.info("Plugins directory does not exist: $pluginsDirectory. Creating...")
            pluginsDir.mkdirs()
            return emptyList()
        }

        if (!pluginsDir.isDirectory) {
            logger.warn("Plugins path is not a directory: $pluginsDirectory")
            return emptyList()
        }

        val jarFiles = pluginsDir.listFiles { file -> file.extension == "jar" } ?: emptyArray()

        if (jarFiles.isEmpty()) {
            logger.info("No plugin JAR files found in $pluginsDirectory")
            return emptyList()
        }

        logger.info("Found ${jarFiles.size} plugin JAR file(s)")

        for (jarFile in jarFiles) {
            try {
                loadPluginFromJar(jarFile)
            } catch (e: Exception) {
                logger.error("Failed to load plugin from ${jarFile.name}: ${e.message}", e)
            }
        }

        return loadedPlugins.toList()
    }

    private fun loadPluginFromJar(jarFile: File) {
        logger.info("Loading plugin from: ${jarFile.name}")

        val classLoader = URLClassLoader(
            arrayOf(jarFile.toURI().toURL()),
            this.javaClass.classLoader
        )

        val serviceLoader = ServiceLoader.load(McpPlugin::class.java, classLoader)

        for (plugin in serviceLoader) {
            logger.info("Loaded plugin: ${plugin.name} v${plugin.version}")
            plugin.initialize()
            loadedPlugins.add(plugin)
        }
    }

    fun shutdownPlugins() {
        for (plugin in loadedPlugins) {
            try {
                plugin.shutdown()
                logger.info("Shut down plugin: ${plugin.name}")
            } catch (e: Exception) {
                logger.error("Error shutting down plugin ${plugin.name}: ${e.message}", e)
            }
        }
        loadedPlugins.clear()
    }
}
