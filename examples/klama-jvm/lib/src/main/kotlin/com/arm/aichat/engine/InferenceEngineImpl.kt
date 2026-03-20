package com.arm.aichat.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.IOException

class InferenceEngineImpl() : InferenceEngine {

    companion object {
        private const val TAG = "InferenceEngine"
        
        @Volatile
        private var instance: InferenceEngine? = null

        fun getInstance() = instance ?: synchronized(this) {
            InferenceEngineImpl().also { instance = it }
        }
    }

    /** JNI Native Methods */
    private external fun initNative()
    private external fun load(modelPath: String): Int
    private external fun prepare(): Int
    private external fun systemInfo(): String
    private external fun updateSamplingParams(temp: Float, topP: Float, topK: Int, penalty: Float)
    private external fun processSystemPrompt(systemPrompt: String): Int
    private external fun processUserPrompt(userPrompt: String, predictLength: Int): Int
    private external fun generateNextToken(): String?
    private external fun unload()
    private external fun shutdown()

    private val _state = MutableStateFlow<InferenceEngine.State>(InferenceEngine.State.Uninitialized)
    override val state: StateFlow<InferenceEngine.State> = _state.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val llamaDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val llamaScope = CoroutineScope(llamaDispatcher + SupervisorJob())

    init {
        llamaScope.launch {
            try {
                _state.value = InferenceEngine.State.Initializing
                
                // On Linux, the library name is libllama-jni.so
                System.loadLibrary("llama-jni")
                
                initNative()
                _state.value = InferenceEngine.State.Initialized
                println("Native library loaded! CPU info: ${systemInfo()}")
            } catch (e: Exception) {
                println("Failed to load native library: ${e.message}")
                _state.value = InferenceEngine.State.Error(e)
            }
        }
    }

    override suspend fun loadModel(pathToModel: String) = withContext(llamaDispatcher) {
        try {
            _state.value = InferenceEngine.State.LoadingModel
            val file = File(pathToModel)
            if (!file.exists()) throw IOException("Model file not found")

            if (load(pathToModel) != 0) throw IOException("C++ side failed to load model")
            if (prepare() != 0) throw IOException("C++ side failed to prepare context")
            
            _state.value = InferenceEngine.State.ModelReady
        } catch (e: Exception) {
            _state.value = InferenceEngine.State.Error(e)
            throw e
        }
    }

    override suspend fun setSystemPrompt(prompt: String) = withContext(llamaDispatcher) {
        _state.value = InferenceEngine.State.ProcessingSystemPrompt
        if (processSystemPrompt(prompt) != 0) throw IOException("Failed to process system prompt")
        _state.value = InferenceEngine.State.ModelReady
    }

    override fun sendUserPrompt(
        message: String, 
        predictLength: Int,
        samplingParams: InferenceEngine.SamplingParams
    ): Flow<String> = flow {
        _state.value = InferenceEngine.State.ProcessingUserPrompt
        
        // Step 1: Tell C++ Scientist to update his sampling tools
        updateSamplingParams(
            samplingParams.temp,
            samplingParams.topP,
            samplingParams.topK,
            samplingParams.repeatPenalty
        )

        // Step 2: Process the prompt
        if (processUserPrompt(message, predictLength) == 0) {
            _state.value = InferenceEngine.State.Generating
            while (true) {
                val token = generateNextToken() ?: break
                if (token.isNotEmpty()) emit(token)
            }
        }
        _state.value = InferenceEngine.State.ModelReady
    }.flowOn(llamaDispatcher)

    override fun cleanUp() {
        runBlocking(llamaDispatcher) {
            unload()
            _state.value = InferenceEngine.State.Initialized
        }
    }

    override fun destroy() {
        runBlocking(llamaDispatcher) {
            shutdown()
            _state.value = InferenceEngine.State.Uninitialized
        }
        llamaScope.cancel()
    }
}
