package ru.chtcholeg.test

/**
 * Test case: Missing return value in when block
 * This should be detected as Critical error
 */
class TestMissingReturn {

    fun getFilteredTools(includeTools: List<String>?, allTools: List<String>): List<String> {
        // ❌ BUG: Missing return value in when block
        val availableTools = when {
            includeTools != null -> {
                val filtered = allTools.filter { it in includeTools }
                if (filtered.isEmpty() && includeTools.isNotEmpty()) {
                    println("WARNING: includeTools=$includeTools resulted in empty tool list")
                }
                // ⚠️ MISSING: filtered (should be here!)
            }
            else -> allTools
        }

        return availableTools // Will fail: Type mismatch (Unit vs List<String>)
    }

    fun getUserName(userId: String?): String {
        // ❌ BUG: Nullable chain without protection
        val user = findUser(userId)
        return user?.name.length.toString() // NPE if user is null or name is null
    }

    private fun findUser(id: String?): User? {
        return null // stub
    }

    data class User(val name: String?)
}
