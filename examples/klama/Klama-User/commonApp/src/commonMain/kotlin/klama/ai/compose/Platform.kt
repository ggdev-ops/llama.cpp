package klama.ai.compose

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform