package com.arm.aichat.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.IOException

internal class InferenceEngineImpl() : InferenceEngine {

    companion object {
        private const val TAG = "InferenceEngine"
        
        @Volatile
        private var instance: InferenceEngine? = null

        fun getInstance() = instance ?: synchronized(this) {
            InferenceEngineImpl().also { instance = it }
        }
    }

    /** JNI Native Methods */
    private external fun initNative(logLevel: Int)
    private external fun load(modelPath: String): Int
    private external fun prepare(contextSize: Int, nPredict: Int): Int
    private external fun resetContextNative()
    private external fun systemInfo(): String
    private external fun benchModel(pp: Int, tg: Int, pl: Int, nr: Int): String
    private external fun updateSamplingParams(temp: Float, topP: Float, topK: Int, penalty: Float)
    private external fun processSystemPrompt(systemPrompt: String): Int
    private external fun processUserPrompt(userPrompt: String, predictLength: Int): Int
    private external fun generateNextToken(): String?
    private external fun getCurrentAssistantMessageNative(): String?  // Renamed native method
    private external fun unload()
    private external fun shutdown()

    private val _state = MutableStateFlow<InferenceEngine.State>(InferenceEngine.State.Uninitialized)
    override val state: StateFlow<InferenceEngine.State> = _state.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val llamaDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val llamaScope = CoroutineScope(llamaDispatcher + SupervisorJob())

    private var isInitialized = false
    private var currentConfig: InferenceEngine.ModelConfig? = null

    init {
        // Don't auto-initialize anymore - wait for explicit initialize() call
    }

    override suspend fun initialize(config: InferenceEngine.ModelConfig) = withContext(llamaDispatcher) {
        try {
            if (isInitialized) {
                println("Engine already initialized")
                return@withContext
            }

            _state.value = InferenceEngine.State.Initializing
            currentConfig = config

            // On Linux, the library name is libllama-jni.so
            System.loadLibrary("llama-jni")

            initNative(config.logLevel.value)
            isInitialized = true
            _state.value = InferenceEngine.State.Initialized
            println("Native library loaded! CPU info: ${systemInfo()}")
        } catch (e: Exception) {
            println("Failed to load native library: ${e.message}")
            _state.value = InferenceEngine.State.Error(e)
            throw e
        }
    }

    override suspend fun loadModel(pathToModel: String) = withContext(llamaDispatcher) {
        check(isInitialized) { "Engine not initialized. Call initialize() first." }
        
        try {
            _state.value = InferenceEngine.State.LoadingModel
            val file = File(pathToModel)
            if (!file.exists()) throw IOException("Model file not found: $pathToModel")

            val loadResult = load(pathToModel)
            if (loadResult != 0) throw IOException("C++ side failed to load model (code: $loadResult)")

            val config = currentConfig ?: InferenceEngine.ModelConfig()
            val prepareResult = prepare(config.contextSize, config.maxPredictLength)
            if (prepareResult != 0) throw IOException("C++ side failed to prepare context (code: $prepareResult)")

            _state.value = InferenceEngine.State.ModelReady
            println("Model loaded successfully with context size: ${config.contextSize}")
        } catch (e: Exception) {
            _state.value = InferenceEngine.State.Error(e)
            throw e
        }
    }

    override suspend fun setSystemPrompt(prompt: String) = withContext(llamaDispatcher) {
        check(_state.value == InferenceEngine.State.ModelReady) { "Model not ready" }
        
        try {
            _state.value = InferenceEngine.State.ProcessingSystemPrompt
            val result = processSystemPrompt(prompt)
            if (result != 0) throw IOException("Failed to process system prompt (code: $result)")
            _state.value = InferenceEngine.State.ModelReady
            println("System prompt set successfully")
        } catch (e: Exception) {
            _state.value = InferenceEngine.State.Error(e)
            throw e
        }
    }

    override suspend fun resetContext() = withContext(llamaDispatcher) {
        check(_state.value != InferenceEngine.State.Uninitialized) { "Engine not initialized" }
        
        resetContextNative()
        _state.value = InferenceEngine.State.ModelReady
        println("Context reset")
    }

    override suspend fun getSystemInfo(): String = withContext(llamaDispatcher) {
        check(isInitialized) { "Engine not initialized" }
        systemInfo()
    }

    override suspend fun benchmark(pp: Int, tg: Int, pl: Int, nr: Int): String = withContext(llamaDispatcher) {
        check(_state.value == InferenceEngine.State.ModelReady) { "Model not ready for benchmarking" }
        
        println("Starting benchmark: pp=$pp, tg=$tg, pl=$pl, nr=$nr")
        val result = benchModel(pp, tg, pl, nr)
        println("Benchmark complete")
        result
    }

    override fun getCurrentAssistantMessage(): String? {
        // This is called from UI thread, so we need to run it on the llama dispatcher
        return runBlocking(llamaDispatcher) {
            check(isInitialized) { "Engine not initialized" }
            getCurrentAssistantMessageNative()  // Call renamed native method
        }
    }

    override fun sendUserPrompt(
        message: String,
        predictLength: Int,
        samplingParams: InferenceEngine.SamplingParams
    ): Flow<String> = channelFlow {
        // Check state before starting
        if (_state.value != InferenceEngine.State.ModelReady) {
            throw IllegalStateException("Model not ready. Current state: ${_state.value}")
        }

        _state.value = InferenceEngine.State.ProcessingUserPrompt

        // Step 1: Update sampling parameters
        updateSamplingParams(
            samplingParams.temp,
            samplingParams.topP,
            samplingParams.topK,
            samplingParams.repeatPenalty
        )

        // Step 2: Process the prompt
        val processResult = withContext(llamaDispatcher) {
            processUserPrompt(message, predictLength)
        }
        if (processResult != 0) {
            _state.value = InferenceEngine.State.ModelReady
            throw IOException("Failed to process user prompt (code: $processResult)")
        }

        _state.value = InferenceEngine.State.Generating

        // Step 3: Stream tokens
        var tokenCount = 0
        var lastProgress = 0L
        val startTime = System.currentTimeMillis()

        try {
            while (true) {
                val token = withContext(llamaDispatcher) {
                    generateNextToken()
                } ?: break
                
                if (token.isNotEmpty()) {
                    send(token)
                    tokenCount++

                    // Log progress every 100ms
                    val now = System.currentTimeMillis()
                    if (now - lastProgress > 100) {
                        val elapsed = (now - startTime) / 1000.0
                        val speed = tokenCount / elapsed
                        println("Generated $tokenCount tokens (${"%.1f".format(speed)} tokens/sec)")
                        lastProgress = now
                    }
                }
            }
        } finally {
            // Always reset state when done
            _state.value = InferenceEngine.State.ModelReady
            val totalTime = (System.currentTimeMillis() - startTime) / 1000.0
            val avgSpeed = tokenCount / totalTime
            println("Generation complete: $tokenCount tokens in ${"%.2f".format(totalTime)}s (${"%.1f".format(avgSpeed)} tokens/sec)")
        }
    }

    override fun cleanUp() {
        runBlocking(llamaDispatcher) {
            if (_state.value != InferenceEngine.State.Uninitialized) {
                unload()
                _state.value = InferenceEngine.State.Initialized
                println("Model unloaded")
            }
        }
    }

    override fun destroy() {
        runBlocking(llamaDispatcher) {
            if (isInitialized) {
                shutdown()
                isInitialized = false
                _state.value = InferenceEngine.State.Uninitialized
                println("Engine destroyed")
            }
        }
        llamaScope.cancel()
    }
}
