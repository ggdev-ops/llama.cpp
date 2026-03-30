package ai.llm

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform