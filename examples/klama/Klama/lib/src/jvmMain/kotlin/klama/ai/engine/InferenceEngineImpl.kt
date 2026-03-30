package klama.ai.engine

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
    private external fun benchModel(pp: Int, tg: Int, pl: Int, nr: Int): String
    private external fun unload()
    private external fun shutdown()

    private val _state = MutableStateFlow<InferenceEngine.State>(InferenceEngine.State.Uninitialized)
    override val state: StateFlow<InferenceEngine.State> = _state.asStateFlow()

    @Volatile
    private var _cancelGeneration = false

    @Volatile
    private var _readyForSystemPrompt = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private val llamaDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val llamaScope = CoroutineScope(llamaDispatcher + SupervisorJob())

    init {
        llamaScope.launch {
            try {
                _state.value = InferenceEngine.State.Initializing
                
                try {
                    val osName = System.getProperty("os.name").lowercase()
                    val libName = when {
                        osName.contains("win") -> "llama-jni.dll"
                        osName.contains("mac") -> "libllama-jni.dylib"
                        else -> "libllama-jni.so"
                    }
                    val url = InferenceEngineImpl::class.java.getResource("/$libName")
                    if (url != null) {
                        val tempFile = File.createTempFile("libllama-jni", null)
                        tempFile.deleteOnExit()
                        url.openStream().use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        System.load(tempFile.absolutePath)
                    } else {
                        // On Linux, the library name is libllama-jni.so
                        System.loadLibrary("llama-jni")
                    }
                } catch (e: UnsatisfiedLinkError) {
                    println("loadLibrary failed, trying absolute path...")
                    // Fallback to absolute path for development
                    val paths = listOf(
                        "lib/build/cmake/libllama-jni.so",
                        "build/cmake/libllama-jni.so"
                    )
                    var loaded = false
                    for (p in paths) {
                        val file = File(p)
                        if (file.exists()) {
                            println("Found library at: ${file.absolutePath}")
                            System.load(file.absolutePath)
                            loaded = true
                            break
                        }
                    }
                    if (!loaded) throw e
                }
                
                initNative()
                _state.value = InferenceEngine.State.Initialized
                println("Native library loaded! CPU info: ${systemInfo()}")
            } catch (e: Throwable) {
                println("Failed to load native library: ${e.message}")
                e.printStackTrace()
                _state.value = InferenceEngine.State.Error(if (e is Exception) e else Exception(e))
            }
        }
    }

    override suspend fun loadModel(pathToModel: String) = withContext(llamaDispatcher) {
        try {
            _state.value = InferenceEngine.State.LoadingModel
            val file = File(pathToModel)
            if (!file.exists()) throw IOException("Model file not found")

            val loadResult = load(pathToModel)
            if (loadResult == 1) {
                _state.value = InferenceEngine.State.Initialized
                throw UnsupportedArchitectureException()
            }
            if (loadResult != 0) throw IOException("C++ side failed to load model")
            if (prepare() != 0) throw IOException("C++ side failed to prepare context")
            
            println("Model loaded!")
            _readyForSystemPrompt = true
            _cancelGeneration = false
            _state.value = InferenceEngine.State.ModelReady
        } catch (e: Exception) {
            _state.value = InferenceEngine.State.Error(e)
            throw e
        }
    }

    override suspend fun setSystemPrompt(prompt: String) = withContext(llamaDispatcher) {
        require(prompt.isNotBlank()) { "Cannot process empty system prompt!" }
        check(_readyForSystemPrompt) { "System prompt must be set ** RIGHT AFTER ** model loaded!" }
        check(_state.value is InferenceEngine.State.ModelReady) {
            "Cannot process system prompt in ${_state.value.javaClass.simpleName}!"
        }

        println("Sending system prompt...")
        _readyForSystemPrompt = false
        _state.value = InferenceEngine.State.ProcessingSystemPrompt
        processSystemPrompt(prompt).let { result ->
            if (result != 0) {
                RuntimeException("Failed to process system prompt: $result").also {
                    _state.value = InferenceEngine.State.Error(it)
                    throw it
                }
            }
        }
        println("System prompt processed! Awaiting user prompt...")
        _state.value = InferenceEngine.State.ModelReady
    }

    override fun sendUserPrompt(
        message: String, 
        predictLength: Int,
        samplingParams: InferenceEngine.SamplingParams
    ): Flow<String> = flow {
        if (_readyForSystemPrompt) {
            println("No system prompt provided. Applying default...")
            setSystemPrompt()
        }

        require(message.isNotEmpty()) { "User prompt discarded due to being empty!" }
        check(_state.value is InferenceEngine.State.ModelReady) {
            "User prompt discarded due to: ${_state.value.javaClass.simpleName}"
        }

        println("Sending user prompt...")
        _readyForSystemPrompt = false
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

    override suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int): String = withContext(llamaDispatcher) {
        check(_state.value is InferenceEngine.State.ModelReady) {
            "Benchmark request discarded due to: $state"
        }
        println("Start benchmark (pp: $pp, tg: $tg, pl: $pl, nr: $nr)")
        _readyForSystemPrompt = false
        _state.value = InferenceEngine.State.Benchmarking
        val result = benchModel(pp, tg, pl, nr)
        _state.value = InferenceEngine.State.ModelReady
        result
    }

    override fun cleanUp() {
        _cancelGeneration = true
        runBlocking(llamaDispatcher) {
            when (val state = _state.value) {
                is InferenceEngine.State.ModelReady -> {
                    println("Unloading model and free resources...")
                    _readyForSystemPrompt = false
                    _state.value = InferenceEngine.State.UnloadingModel
                    unload()
                    _state.value = InferenceEngine.State.Initialized
                }
                is InferenceEngine.State.Error -> {
                    println("Clearing error state...")
                    _readyForSystemPrompt = false
                    _state.value = InferenceEngine.State.Initialized
                }
                is InferenceEngine.State.Uninitialized, 
                is InferenceEngine.State.Initialized -> {
                    // Do nothing
                }
                else -> {
                    println("Clean up aborted due to: $state")
                }
            }
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
