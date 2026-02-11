package ru.chtcholeg.agent.domain.service

import java.io.File

/**
 * Desktop implementation of ProjectRootProvider.
 * Searches for README.md using multiple strategies with prioritization for root README:
 * 1. Git repository root (.git folder) - HIGHEST PRIORITY for root README.md
 * 2. Gradle project root (settings.gradle.kts) - if Git root not found
 * 3. Directory hierarchy search - fallback (up to 5 levels, picks highest-level README)
 *
 * Note: Always prioritizes the root README.md from Git repository root to ensure
 * that /help command displays project-wide structure, not module-specific README.
 */
class DesktopProjectRootProvider : ProjectRootProvider {

    companion object {
        private const val MAX_FILE_SIZE_BYTES = 100 * 1024 // 100KB
    }

    override suspend fun readReadmeFile(): String {
        val root = resolveProjectRoot()
        if (root != null) {
            val readmeFile = File(root, "README.md")
            if (readmeFile.exists() && readmeFile.isFile) {
                println("[ProjectRootProvider] Found README.md in project root: ${root.absolutePath}")
                return readmeFile.readText()
            }
        }

        // Fallback: search directory hierarchy for highest-level README
        val workingDir = File(System.getProperty("user.dir"))
        var currentDir = workingDir
        var rootReadmeFile: File? = null
        repeat(5) { level ->
            println("[ProjectRootProvider] Checking level $level: ${currentDir.absolutePath}")
            val readmeFile = File(currentDir, "README.md")
            if (readmeFile.exists() && readmeFile.isFile) {
                println("[ProjectRootProvider] Found README.md candidate at: ${readmeFile.absolutePath}")
                rootReadmeFile = readmeFile
            }
            currentDir = currentDir.parentFile ?: run {
                println("[ProjectRootProvider] Reached filesystem root, stopping search")
                return@repeat
            }
        }
        if (rootReadmeFile != null) {
            println("[ProjectRootProvider] Using root README.md: ${rootReadmeFile!!.absolutePath}")
            return rootReadmeFile!!.readText()
        }

        throw IllegalStateException(
            "README.md not found in project directory hierarchy. " +
                "Working dir: ${workingDir.absolutePath}"
        )
    }

    override suspend fun readClaudeMdFile(): String? {
        val root = resolveProjectRoot() ?: return null
        val claudeMdFile = File(root, "CLAUDE.md")
        if (claudeMdFile.exists() && claudeMdFile.isFile) {
            println("[ProjectRootProvider] Found CLAUDE.md in project root: ${root.absolutePath}")
            return claudeMdFile.readText()
        }
        println("[ProjectRootProvider] CLAUDE.md not found in project root")
        return null
    }

    override suspend fun readProjectFile(relativePath: String): String? {
        val root = resolveProjectRoot() ?: return null
        val file = File(root, relativePath)

        // Path traversal protection: ensure resolved path is within project root
        val rootCanonical = root.canonicalPath
        val fileCanonical = file.canonicalPath
        if (!fileCanonical.startsWith(rootCanonical)) {
            println("[ProjectRootProvider] Blocked path traversal attempt: $relativePath")
            return null
        }

        if (!file.exists() || !file.isFile) {
            println("[ProjectRootProvider] File not found: $relativePath")
            return null
        }

        if (file.length() > MAX_FILE_SIZE_BYTES) {
            println("[ProjectRootProvider] File too large (${file.length()} bytes), skipping: $relativePath")
            return null
        }

        return file.readText()
    }

    /**
     * Resolve the project root directory using Git root or Gradle project root.
     */
    private fun resolveProjectRoot(): File? {
        val workingDir = File(System.getProperty("user.dir"))
        println("[ProjectRootProvider] Working directory: ${workingDir.absolutePath}")

        // Strategy 1: Git repository root (highest priority)
        val gitRoot = findGitRoot(workingDir)
        if (gitRoot != null) {
            println("[ProjectRootProvider] Found Git root: ${gitRoot.absolutePath}")
            return gitRoot
        }

        // Strategy 2: Gradle project root
        val projectRoot = findProjectRoot(workingDir)
        if (projectRoot != null) {
            println("[ProjectRootProvider] Found Gradle project root: ${projectRoot.absolutePath}")
            return projectRoot
        }

        println("[ProjectRootProvider] Could not resolve project root")
        return null
    }

    /**
     * Find Gradle project root by looking for settings.gradle.kts or settings.gradle.
     * Searches up the directory hierarchy (up to 10 levels).
     */
    private fun findProjectRoot(startDir: File): File? {
        var current: File? = startDir
        var level = 0
        while (current != null && level < 10) {
            if (File(current, "settings.gradle.kts").exists() || File(current, "settings.gradle").exists()) {
                return current
            }
            current = current.parentFile
            level++
        }
        return null
    }

    /**
     * Find Git repository root by looking for .git folder.
     * Searches up the directory hierarchy.
     */
    private fun findGitRoot(startDir: File): File? {
        var current: File? = startDir
        while (current != null) {
            val gitDir = File(current, ".git")
            if (gitDir.exists() && gitDir.isDirectory) {
                return current
            }
            current = current.parentFile
        }
        return null
    }
}

/**
 * Create a desktop-specific ProjectRootProvider instance.
 */
actual fun createProjectRootProvider(): ProjectRootProvider =
    DesktopProjectRootProvider()
