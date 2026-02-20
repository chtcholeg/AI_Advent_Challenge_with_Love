package ru.chtcholeg.agent.data.agent

import ru.chtcholeg.agent.domain.model.AgentDefinition

/**
 * Registry of available specialized agents.
 * Based on Claude Code agent types.
 */
object AgentRegistry {

    /**
     * Get all available agents.
     */
    fun getAllAgents(): List<AgentDefinition> = listOf(
        // Exploration & Planning
        exploreAgent,
        planAgent,

        // General Purpose
        generalPurposeAgent,

        // Language Specialists
        pythonProAgent,
        kotlinSpecialistAgent,
        typescriptProAgent,
        javaArchitectAgent,
        rustEngineerAgent,

        // Backend & Infrastructure
        backendDeveloperAgent,
        devopsEngineerAgent,
        databaseOptimizerAgent,

        // Frontend & Mobile
        frontendDeveloperAgent,
        reactSpecialistAgent,
        mobileAppDeveloperAgent,

        // Quality & Testing
        qaExpertAgent,
        testAutomatorAgent,
        codeReviewerAgent,
        debuggerAgent,

        // Architecture & Design
        architectReviewerAgent,
        refactoringSpecialistAgent,
        apiDesignerAgent,

        // Documentation & Content
        technicalWriterAgent,
        apiDocumenterAgent,

        // Support & Customer Service
        supportAssistantAgent
    )

    /**
     * Get agent by type.
     */
    fun getAgent(type: String): AgentDefinition? {
        return getAllAgents().find { it.type == type }
    }

    // Tool groups for reuse
    private val READ_ONLY_TOOLS = listOf("read", "glob", "grep")
    private val PLAN_TOOLS = READ_ONLY_TOOLS + listOf("write", "ask_user_question")
    private val DEV_TOOLS = READ_ONLY_TOOLS + listOf("write", "edit", "bash")
    private val FULL_TOOLS_NO_SUBAGENT = DEV_TOOLS + listOf(
        "ask_user_question",
        //"task_create", "task_update", "task_list", "task_get",
        "enter_plan_mode", "exit_plan_mode"
    )

    // Agent Definitions

    private val exploreAgent = AgentDefinition(
        type = "Explore",
        name = "Explore Agent",
        description = "Fast agent specialized for exploring codebases. Use for finding files by patterns, searching code, or answering questions about the codebase.",
        capabilities = listOf(
            "Find files by patterns (e.g., src/components/**/*.tsx)",
            "Search code for keywords",
            "Answer questions about codebase structure",
            "Quick, medium, or thorough exploration modes"
        ),
        model = "haiku",  // Fast model for quick exploration
        maxTurns = 15,
        allowedTools = READ_ONLY_TOOLS
    )

    private val planAgent = AgentDefinition(
        type = "Plan",
        name = "Plan Agent",
        description = "Software architect agent for designing implementation plans. Returns step-by-step plans, identifies critical files, considers architectural trade-offs.",
        capabilities = listOf(
            "Design implementation strategies",
            "Identify critical files and dependencies",
            "Consider architectural trade-offs",
            "Create step-by-step execution plans"
        ),
        maxTurns = 20,
        allowedTools = PLAN_TOOLS
    )

    private val generalPurposeAgent = AgentDefinition(
        type = "general-purpose",
        name = "General Purpose Agent",
        description = "General-purpose agent for complex questions, code search, and multi-step tasks. Use when not confident about finding match in first few tries.",
        capabilities = listOf(
            "Research complex questions",
            "Search for code patterns",
            "Execute multi-step tasks",
            "Autonomous problem solving"
        ),
        maxTurns = 25,
        allowedTools = FULL_TOOLS_NO_SUBAGENT
    )

    // Language Specialists

    private val pythonProAgent = AgentDefinition(
        type = "python-pro",
        name = "Python Expert",
        description = "Expert Python developer specializing in modern Python 3.11+ with type safety, async programming, data science, and web frameworks.",
        capabilities = listOf(
            "Modern Python 3.11+ development",
            "Type hints and mypy",
            "Async/await patterns",
            "FastAPI, Django, Flask",
            "Data science (pandas, numpy)"
        ),
        maxTurns = 20,
        allowedTools = DEV_TOOLS
    )

    private val kotlinSpecialistAgent = AgentDefinition(
        type = "kotlin-specialist",
        name = "Kotlin Specialist",
        description = "Expert Kotlin developer specializing in coroutines, multiplatform development, and Android applications.",
        capabilities = listOf(
            "Kotlin coroutines and Flow",
            "Kotlin Multiplatform",
            "Android development",
            "Compose UI",
            "Ktor and server-side Kotlin"
        ),
        maxTurns = 20,
        allowedTools = DEV_TOOLS
    )

    private val typescriptProAgent = AgentDefinition(
        type = "typescript-pro",
        name = "TypeScript Expert",
        description = "Expert TypeScript developer with advanced type system usage, full-stack development, and build optimization.",
        capabilities = listOf(
            "Advanced TypeScript types",
            "Full-stack TypeScript",
            "React, Vue, Angular",
            "Node.js backend",
            "Build optimization"
        ),
        maxTurns = 20,
        allowedTools = DEV_TOOLS
    )

    private val javaArchitectAgent = AgentDefinition(
        type = "java-architect",
        name = "Java Architect",
        description = "Senior Java architect specializing in enterprise-grade applications, Spring ecosystem, and cloud-native development.",
        capabilities = listOf(
            "Java 17+ features",
            "Spring Boot and Spring Cloud",
            "Microservices architecture",
            "Enterprise patterns",
            "Performance optimization"
        ),
        maxTurns = 20,
        allowedTools = DEV_TOOLS
    )

    private val rustEngineerAgent = AgentDefinition(
        type = "rust-engineer",
        name = "Rust Engineer",
        description = "Expert Rust developer specializing in systems programming, memory safety, and zero-cost abstractions.",
        capabilities = listOf(
            "Ownership and borrowing",
            "Async Rust",
            "Systems programming",
            "WebAssembly",
            "Performance optimization"
        ),
        maxTurns = 20,
        allowedTools = DEV_TOOLS
    )

    // Backend & Infrastructure

    private val backendDeveloperAgent = AgentDefinition(
        type = "backend-developer",
        name = "Backend Developer",
        description = "Senior backend engineer specializing in scalable API development and microservices architecture.",
        capabilities = listOf(
            "RESTful API design",
            "Database design and optimization",
            "Microservices patterns",
            "Authentication and authorization",
            "Caching strategies"
        ),
        maxTurns = 20,
        allowedTools = DEV_TOOLS
    )

    private val devopsEngineerAgent = AgentDefinition(
        type = "devops-engineer",
        name = "DevOps Engineer",
        description = "Expert DevOps engineer bridging development and operations with CI/CD, containerization, and cloud platforms.",
        capabilities = listOf(
            "CI/CD pipeline setup",
            "Docker and Kubernetes",
            "Infrastructure as Code",
            "Monitoring and logging",
            "Cloud platforms (AWS, Azure, GCP)"
        ),
        maxTurns = 20,
        allowedTools = DEV_TOOLS
    )

    private val databaseOptimizerAgent = AgentDefinition(
        type = "database-optimizer",
        name = "Database Optimizer",
        description = "Expert database optimizer specializing in query optimization, performance tuning, and scalability.",
        capabilities = listOf(
            "Query optimization",
            "Index strategies",
            "Execution plan analysis",
            "Database scaling",
            "Performance profiling"
        ),
        maxTurns = 20,
        allowedTools = DEV_TOOLS
    )

    // Frontend & Mobile

    private val frontendDeveloperAgent = AgentDefinition(
        type = "frontend-developer",
        name = "Frontend Developer",
        description = "Expert UI engineer focused on crafting robust, scalable frontend solutions with high-quality components.",
        capabilities = listOf(
            "Modern JavaScript/TypeScript",
            "React, Vue, or Angular",
            "Responsive design",
            "Performance optimization",
            "Accessibility (a11y)"
        ),
        maxTurns = 20,
        allowedTools = DEV_TOOLS
    )

    private val reactSpecialistAgent = AgentDefinition(
        type = "react-specialist",
        name = "React Specialist",
        description = "Expert React specialist mastering React 18+ with modern patterns, hooks, and server components.",
        capabilities = listOf(
            "React 18+ features",
            "Custom hooks",
            "Server components",
            "State management (Redux, Zustand)",
            "Performance optimization"
        ),
        maxTurns = 20,
        allowedTools = DEV_TOOLS
    )

    private val mobileAppDeveloperAgent = AgentDefinition(
        type = "mobile-app-developer",
        name = "Mobile App Developer",
        description = "Expert mobile app developer specializing in native and cross-platform development for iOS and Android.",
        capabilities = listOf(
            "React Native",
            "Flutter",
            "Native iOS (Swift)",
            "Native Android (Kotlin)",
            "Mobile UI/UX patterns"
        ),
        maxTurns = 20,
        allowedTools = DEV_TOOLS
    )

    // Quality & Testing

    private val qaExpertAgent = AgentDefinition(
        type = "qa-expert",
        name = "QA Expert",
        description = "Expert QA engineer specializing in comprehensive quality assurance, test strategy, and quality metrics.",
        capabilities = listOf(
            "Test strategy and planning",
            "Manual testing",
            "Quality metrics",
            "Bug reporting",
            "Test case design"
        ),
        maxTurns = 15,
        allowedTools = READ_ONLY_TOOLS + listOf("bash")
    )

    private val testAutomatorAgent = AgentDefinition(
        type = "test-automator",
        name = "Test Automator",
        description = "Expert test automation engineer building robust test frameworks and comprehensive test coverage.",
        capabilities = listOf(
            "Test automation frameworks",
            "Unit testing (Jest, pytest, JUnit)",
            "Integration testing",
            "E2E testing (Playwright, Selenium)",
            "CI/CD integration"
        ),
        maxTurns = 20,
        allowedTools = DEV_TOOLS
    )

    private val codeReviewerAgent = AgentDefinition(
        type = "code-reviewer",
        name = "Code Reviewer",
        description = "Expert code reviewer specializing in code quality, security vulnerabilities, and best practices.",
        capabilities = listOf(
            "Code quality analysis",
            "Security vulnerability detection",
            "Performance optimization suggestions",
            "Best practices enforcement",
            "Design pattern recognition"
        ),
        maxTurns = 20,
        allowedTools = READ_ONLY_TOOLS
    )

    private val debuggerAgent = AgentDefinition(
        type = "debugger",
        name = "Debugger",
        description = "Expert debugger specializing in complex issue diagnosis, root cause analysis, and systematic problem-solving.",
        capabilities = listOf(
            "Root cause analysis",
            "Log analysis",
            "Stack trace interpretation",
            "Debugging tools expertise",
            "Issue reproduction"
        ),
        maxTurns = 20,
        allowedTools = READ_ONLY_TOOLS + listOf("bash")
    )

    // Architecture & Design

    private val architectReviewerAgent = AgentDefinition(
        type = "architect-reviewer",
        name = "Architect Reviewer",
        description = "Expert architecture reviewer specializing in system design validation and architectural patterns.",
        capabilities = listOf(
            "Architecture review",
            "Design pattern evaluation",
            "Scalability assessment",
            "Technology stack validation",
            "Trade-off analysis"
        ),
        maxTurns = 20,
        allowedTools = READ_ONLY_TOOLS
    )

    private val refactoringSpecialistAgent = AgentDefinition(
        type = "refactoring-specialist",
        name = "Refactoring Specialist",
        description = "Expert refactoring specialist mastering safe code transformation and design pattern application.",
        capabilities = listOf(
            "Code smell detection",
            "Refactoring patterns",
            "Test-driven refactoring",
            "Legacy code modernization",
            "Complexity reduction"
        ),
        maxTurns = 20,
        allowedTools = DEV_TOOLS
    )

    private val apiDesignerAgent = AgentDefinition(
        type = "api-designer",
        name = "API Designer",
        description = "API architecture expert designing scalable, developer-friendly interfaces with comprehensive documentation.",
        capabilities = listOf(
            "RESTful API design",
            "GraphQL schema design",
            "API versioning strategies",
            "OpenAPI/Swagger specs",
            "API security"
        ),
        maxTurns = 20,
        allowedTools = READ_ONLY_TOOLS + listOf("write")
    )

    // Documentation & Content

    private val technicalWriterAgent = AgentDefinition(
        type = "technical-writer",
        name = "Technical Writer",
        description = "Expert technical writer specializing in clear, accurate documentation and content creation.",
        capabilities = listOf(
            "API documentation",
            "User guides",
            "Architecture documentation",
            "Code comments",
            "README files"
        ),
        maxTurns = 15,
        allowedTools = READ_ONLY_TOOLS + listOf("write")
    )

    private val apiDocumenterAgent = AgentDefinition(
        type = "api-documenter",
        name = "API Documenter",
        description = "Expert API documenter creating comprehensive, developer-friendly API documentation.",
        capabilities = listOf(
            "OpenAPI/Swagger documentation",
            "Interactive API docs",
            "Code examples",
            "Authentication guides",
            "API versioning docs"
        ),
        maxTurns = 15,
        allowedTools = READ_ONLY_TOOLS + listOf("write")
    )

    // Support & Customer Service

    private val supportAssistantAgent = AgentDefinition(
        type = "support-assistant",
        name = "User Support Assistant",
        description = "Expert support assistant for GigaChat Multiplatform Chat App. Combines CRM tools, RAG knowledge base, and empathetic communication to provide personalized user support.",
        capabilities = listOf(
            "Access user context via CRM (tickets, history, plan)",
            "Search knowledge base via RAG (FAQ, docs, solutions)",
            "Provide personalized support based on user data",
            "Update ticket status and add notes",
            "Escalate complex issues when needed",
            "Follow support workflow and quality metrics"
        ),
        maxTurns = 25,
        allowedTools = FULL_TOOLS_NO_SUBAGENT + listOf("rag_search")  // All tools + RAG
    )
}
