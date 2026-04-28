package android.llama.cpp

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class LLamaAndroid {
    private val tag: String? = this::class.simpleName

    interface ProgressCallback {
        fun onProgress(progress: Float)
    }

    private val stateRef: AtomicReference<State> = AtomicReference(State.Idle)
    private val inferenceMutex = Mutex()

    private val runLoop: CoroutineDispatcher = Executors.newSingleThreadExecutor {
        thread(start = false, name = "Llm-RunLoop") {
            Log.d(tag, "Dedicated thread for native code: ${Thread.currentThread().name}")

            // No-op if called more than once.
            System.loadLibrary("llama-android")

            // Set llama log handler to Android
            log_to_android()
            // backend_init(false) // MOVED to explicit initBackend() call

            Log.d(tag, system_info())

            it.run()
        }.apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, exception: Throwable ->
                Log.e(tag, "Unhandled exception", exception)
            }
        }
    }.asCoroutineDispatcher()

    // Dynamic defaults updated via LlamaCppManager
    var nctx: Int = 4096
    var nlen: Int = 1024

    private external fun log_to_android()
    private external fun get_hardware_status(): String
    private external fun get_model_n_ctx_train(model: Long): Int
    private external fun nativeGetHeartbeat(): Int
    private external fun load_model(filename: String, backendMode: Int, callback: ProgressCallback?): Long
    private external fun load_model_from_fd(fd: Int, offset: Long, size: Long, backendMode: Int, callback: ProgressCallback?): Long
    private external fun free_model(model: Long)
    private external fun new_context(model: Long, nCtx: Int): Long
    private external fun free_context(context: Long)
    private external fun backend_init(numa: Boolean, filesDir: String)
    private external fun backend_free()
    private external fun save_state(context: Long, filename: String): Boolean
    private external fun load_state(context: Long, filename: String): Boolean
    private external fun new_batch(nTokens: Int, embd: Int, nSeqMax: Int): Long
    private external fun free_batch(batch: Long)
    private external fun new_sampler(context: Long, grammar: String?, topK: Int, topP: Float, temp: Float): Long
    private external fun free_sampler(sampler: Long)
    private external fun bench_model(
        context: Long,
        model: Long,
        batch: Long,
        pp: Int,
        tg: Int,
        pl: Int,
        nr: Int
    ): String

    private external fun system_info(): String

    fun getHardwareStatus(): String {
        return try {
            get_hardware_status()
        } catch (e: Exception) {
            "CPU [Fallback]"
        }
    }

    fun getModelContextLimit(): Int {
        return when (val state = stateRef.get()) {
            is State.Loaded -> get_model_n_ctx_train(state.model)
            else -> 0
        }
    }

    suspend fun getHeartbeat(): Int {
        return withContext(runLoop) {
            nativeGetHeartbeat()
        }
    }

    suspend fun init(filesDir: String) {
        withContext(runLoop) {
            backend_init(false, filesDir)
        }
    }

    private external fun completion_init(
        context: Long,
        batch: Long,
        text: String,
        formatChat: Boolean,
        nLen: Int
    ): Int

    private external fun completion_loop(
        context: Long,
        batch: Long,
        sampler: Long,
        nLen: Int,
        ncur: IntVar
    ): String?

    private external fun kv_cache_clear(context: Long)
    private external fun set_trim_level(level: Int)
    
    // SAR PHASE 3: SHM & Recovery
    private external fun native_shm_attach(fd: Int, size: Long): Long
    private external fun native_kv_restore(context: Long, seqId: Int, lastPos: Int)
    private external fun native_shm_verify(ptr: Long, size: Long, expectedHash: String): Boolean
    private external fun native_inject_token(context: Long, tokenId: Int, kvOffset: Int)

    // ENTERPRISE: Native GGUF Embeddings (Piggyback Protocol)
    private external fun native_get_embeddings(model: Long, text: String): FloatArray

    suspend fun kvCacheClear() = withContext(runLoop) {
        when (val state = stateRef.get()) {
            is State.Loaded -> kv_cache_clear(state.context)
            else -> {}
        }
    }

    /**
     *  SOLARIS PHASE 3: Re-attaches context to a warm tensor buffer in SHM.
     * Native takes ownership of [fd] and closes it post-mmap.
     */
    suspend fun attachShm(fd: Int, size: Long) = withContext(runLoop) {
        val ptr = native_shm_attach(fd, size)
        if (ptr == 0L) throw IllegalStateException("native_shm_attach failed")
        // Implementation note: Further state transition logic will follow in finalizeLoading 
    }

    suspend fun kvRestore(seqId: Int, lastPos: Int) = withContext(runLoop) {
        when (val state = stateRef.get()) {
            is State.Loaded -> native_kv_restore(state.context, seqId, lastPos)
            else -> throw IllegalStateException("No context to restore")
        }
    }

    suspend fun injectToken(tokenId: Int, kvOffset: Int) = withContext(runLoop) {
        when (val state = stateRef.get()) {
            is State.Loaded -> native_inject_token(state.context, tokenId, kvOffset)
            else -> throw IllegalStateException("No context to inject")
        }
    }

    /**
     *  SOLARIS SFE: Signals the native engine to perform a bounded trim
     * on the next decode cycle to recover RAM under system pressure.
     */
    fun setTrimLevel(level: Int) {
        set_trim_level(level)
    }

    fun isReady(): Boolean = stateRef.get() is State.Loaded

    /**
     * ENTERPRISE: Generates a GGUF embedding vector for the given text.
     * Spawns a temporary lightweight context (n_ctx=512) on the already-loaded model pointer
     * so the main chat KV cache is never disturbed. Returns FloatArray(0) on any failure.
     * Must only be called when the engine is in State.Loaded.
     */
    suspend fun getEmbeddings(text: String): FloatArray {
        return withContext(runLoop) {
            val state = stateRef.get()
            if (state !is State.Loaded) {
                Log.e(tag, "[EMBED] getEmbeddings() called but model is not loaded.")
                return@withContext FloatArray(0)
            }
            try {
                val result = native_get_embeddings(state.model, text)
                Log.i(tag, "[EMBED] Got ${result.size}-dim embedding from native layer.")
                result
            } catch (e: Exception) {
                Log.e(tag, "[EMBED] native_get_embeddings threw: ${e.message}")
                FloatArray(0)
            }
        }
    }

    suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1): String {
        return inferenceMutex.withLock {
            withContext(runLoop) {
                when (val state = stateRef.get()) {
                    is State.Loaded -> {
                        Log.d(tag, "bench(): $state")
                        bench_model(state.context, state.model, state.batch, pp, tg, pl, nr)
                    }

                    else -> throw IllegalStateException("No model loaded")
                }
            }
        }
    }

    suspend fun load(
        pathToModel: String, 
        backendMode: Int = 0, 
        grammar: String? = null,
        topK: Int = 51,
        topP: Float = 0.95f,
        temp: Float = 0.8f,
        progressCallback: ProgressCallback? = null
    ): Long {
        return inferenceMutex.withLock {
            withContext(runLoop) {
                when (stateRef.get()) {
                    is State.Idle -> {
                        val model = load_model(pathToModel, backendMode, progressCallback)
                        if (model == -2L) {
                             Log.e(tag, " [SENTINEL] Pollution detected during load. Hard reset signal sent.")
                             return@withContext -2L
                        }
                        if (model <= 0L) throw IllegalStateException("load_model() failed with code $model")
                        try {
                            finalizeLoading(model, pathToModel, grammar, topK, topP, temp)
                        } catch (e: Exception) {
                            Log.e(tag, " [SAR] Hardware Incompatibility detected during finalize: ${e.message}")
                            return@withContext -3L
                        }
                        model
                    }
                    is State.Loaded -> 1L // Already loaded
                }
            }
        }
    }

    suspend fun loadFromFd(
        fd: Int,
        offset: Long,
        size: Long,
        backendMode: Int = 0,
        grammar: String? = null,
        topK: Int = 51,
        topP: Float = 0.95f,
        temp: Float = 0.8f,
        progressCallback: ProgressCallback? = null
    ): Long {
        return inferenceMutex.withLock {
            withContext(runLoop) {
                when (stateRef.get()) {
                    is State.Idle -> {
                        val model = load_model_from_fd(fd, offset, size, backendMode, progressCallback)
                        if (model == -2L) {
                            Log.e(tag, " [SENTINEL] Pollution detected during FD load. Hard reset signal sent.")
                            return@withContext -2L
                        }
                        if (model <= 0L) throw IllegalStateException("load_model_from_fd() failed with code $model")
                        try {
                            finalizeLoading(model, "FD://$fd", grammar, topK, topP, temp)
                        } catch (e: Exception) {
                            Log.e(tag, " [SAR] Hardware Incompatibility detected during FD finalize: ${e.message}")
                            return@withContext -3L
                        }
                        model
                    }
                    is State.Loaded -> 1L // Already loaded
                }
            }
        }
    }

    private fun finalizeLoading(
        model: Long,
        label: String,
        grammar: String?,
        topK: Int,
        topP: Float,
        temp: Float
    ) {
        //  Enterprise Fix: Limit context by model training capacity if smaller than requested
        val trainLimit = get_model_n_ctx_train(model)
        val finalCtx = if (trainLimit > 0 && nctx > trainLimit) {
            Log.w(tag, "Requested context $nctx exceeds model training limit $trainLimit. Clamping to limit.")
            trainLimit
        } else {
            nctx
        }

        val context = new_context(model, finalCtx)
        if (context == 0L) throw IllegalStateException("new_context() failed")

        // Dynamic batch size based on context to prevent memory waste/overflow
        val batchSize = if (nctx > 2048) 2048 else nctx
        val batch = new_batch(batchSize, 0, 1)
        if (batch == 0L) throw IllegalStateException("new_batch() failed")

        val sampler = new_sampler(context, grammar, topK, topP, temp)
        if (sampler == 0L) throw IllegalStateException("new_sampler() failed")

        Log.i(tag, "Loaded model successfully: $label")
        stateRef.set(State.Loaded(model, context, batch, sampler))
    }

    fun send(
        message: String, 
        formatChat: Boolean = false,
        topK: Int = 51,
        topP: Float = 0.95f,
        temp: Float = 0.8f
    ): Flow<String> = flow {
        inferenceMutex.withLock {
            when (val state = stateRef.get()) {
                is State.Loaded -> {
                    // Update sampler with new parameters if they differ (or just refresh)
                    withContext(runLoop) {
                        try {
                            free_sampler(state.sampler)
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to free old sampler", e)
                        }
                        val newSmpl = new_sampler(state.context, null, topK, topP, temp)
                        stateRef.set(state.copy(sampler = newSmpl))
                    }
                    
                    val newState = stateRef.get() as State.Loaded
                    //  Enterprise Fix: Clear KV cache BEFORE prompt evaluation
                    kv_cache_clear(newState.context)

                    val processed = completion_init(newState.context, newState.batch, message, formatChat, nlen)
                    if (processed <= 0) {
                        emit("Error: Prompt too long (Max ${newState.context})")
                        return@flow
                    }

                    val ncur = IntVar(processed)
                    val stopLimit = processed + nlen

                    while (ncur.value < stopLimit) {
                        val str = completion_loop(newState.context, newState.batch, newState.sampler, stopLimit, ncur)
                        if (str == null) {
                            break
                        }
                        emit(str)
                    }
                }
                else -> {}
            }
        }
    }.flowOn(runLoop)

    /**
     * Unloads the model and frees resources.
     *
     * This is a no-op if there's no model loaded.
     */
    suspend fun unload() {
        inferenceMutex.withLock {
            withContext(runLoop) {
                when (val state = stateRef.get()) {
                    is State.Loaded -> {
                        //  PROTOCOL: Llama C++ Memory Isolation (AGENTS.md Section 3)
                        // Rigorous nested try-finally to guarantee deallocation of ALL pointers.
                        try {
                            try {
                                free_sampler(state.sampler)
                            } catch (e: Exception) {
                                Log.e(tag, "JNI free_sampler failed", e)
                            } finally {
                                try {
                                    free_batch(state.batch)
                                } catch (e: Exception) {
                                    Log.e(tag, "JNI free_batch failed", e)
                                } finally {
                                    try {
                                        free_context(state.context)
                                    } catch (e: Exception) {
                                        Log.e(tag, "JNI free_context failed", e)
                                    }
                                }
                            }
                        } finally {
                            try {
                                free_model(state.model)
                            } catch (e: Exception) {
                                Log.e(tag, "JNI free_model failed", e)
                            } finally {
                                stateRef.set(State.Idle)
                                Log.i(tag, "LLaMA JNI C++ Memory Isolation strictly freed.")
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    suspend fun saveSession(path: String): Boolean = withContext(runLoop) {
        val state = stateRef.get()
        if (state is State.Loaded) {
            save_state(state.context, path)
        } else false
    }

    suspend fun loadSession(path: String): Boolean = withContext(runLoop) {
        val state = stateRef.get()
        if (state is State.Loaded) {
            load_state(state.context, path)
        } else false
    }

    companion object {
        private class IntVar(value: Int) {
            @Volatile
            var value: Int = value
                private set

            fun inc() {
                synchronized(this) {
                    value += 1
                }
            }
        }

        private sealed interface State {
            data object Idle: State
            data class Loaded(val model: Long, val context: Long, val batch: Long, val sampler: Long): State
        }

        // Enforce only one instance of Llm.
        private val _instance: LLamaAndroid = LLamaAndroid()

        fun instance(): LLamaAndroid = _instance
    }
}
