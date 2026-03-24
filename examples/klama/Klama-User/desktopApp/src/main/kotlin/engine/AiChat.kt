package klama.ai.compose.engine

import klama.ai.compose.engine.internal.InferenceEngineImpl

/**
 * Main entry point for Arm's AI Chat library.
 */
object AiChat {
    /**
     * Get the inference engine single instance.
     */
    fun getInferenceEngine() = InferenceEngineImpl.getInstance()
}
