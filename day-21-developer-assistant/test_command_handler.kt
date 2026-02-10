import java.io.File

/**
 * Standalone test to verify ProjectRootProvider logic
 */
fun main() {
    println("=== Testing ProjectRootProvider Logic ===")
    println()

    val workingDir = File(System.getProperty("user.dir"))
    println("Working directory: ${workingDir.absolutePath}")
    println()

    // Test Git root search
    println("1. Testing Git root search...")
    val gitRoot = findGitRoot(workingDir)
    if (gitRoot != null) {
        println("   ✅ Git root found: ${gitRoot.absolutePath}")
        val readmeInGitRoot = File(gitRoot, "README.md")
        if (readmeInGitRoot.exists()) {
            println("   ✅ README.md found in Git root")
        } else {
            println("   ℹ️  README.md not found in Git root (will search hierarchy)")
        }
    } else {
        println("   ℹ️  Git root not found")
    }
    println()

    // Test hierarchy search
    println("2. Testing hierarchy search...")
    var currentDir = workingDir
    var found = false
    repeat(5) { level ->
        println("   Level $level: ${currentDir.absolutePath}")
        val readmeFile = File(currentDir, "README.md")
        if (readmeFile.exists() && readmeFile.isFile) {
            println("   ✅ Found README.md!")
            println("   📄 Size: ${readmeFile.length()} bytes")
            println()

            // Read first few lines
            println("3. Reading content...")
            val content = readmeFile.readText()
            val lines = content.lines().take(10)
            println("   First 10 lines:")
            lines.forEach { line ->
                println("   | ${line.take(80)}")
            }

            found = true
            return@repeat
        }
        currentDir = currentDir.parentFile ?: return@repeat
    }

    if (!found) {
        println("   ❌ README.md not found in hierarchy")
    }
}

fun findGitRoot(startDir: File): File? {
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
