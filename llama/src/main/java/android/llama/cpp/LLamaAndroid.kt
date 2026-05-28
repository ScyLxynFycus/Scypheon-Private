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
import android.hardware.HardwareBuffer

/**
 * LLamaAndroid: High-performance JNI wrapper for llama.cpp.
 * [SAR PHASE 2/3] Hardened for zero-copy IPC and crash resilience.
 */
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
            // Setup Native Logging
            log_to_android()
            Log.d(tag, system_info())
            it.run()
        }.apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, exception: Throwable ->
                Log.e(tag, "💀 FATAL NATIVE EXCEPTION in Llm-RunLoop", exception)
                // In Enterprise systems, you should have a recovery mechanism here.
            }
        }
    }.asCoroutineDispatcher()

    var nlen: Int = 1024
    var nctx: Int = 4096  // [v1.1.3-SAR] Tracks active context size for decode-loop clamping

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
    private external fun bench_model(context: Long, model: Long, batch: Long, pp: Int, tg: Int, pl: Int, nr: Int): String
    private external fun system_info(): String
    private external fun completion_init(context: Long, batch: Long, text: String, formatChat: Boolean, nLen: Int): Int
    
    // OPTIMASI: Gunakan IntArray alih-alih objek kelas untuk performa JNI
    private external fun completion_loop(context: Long, batch: Long, sampler: Long, nLen: Int, ncur: IntArray): String?
    
    private external fun kv_cache_clear(context: Long)
    private external fun set_trim_level(level: Int)
    private external fun native_shm_attach(fd: Int, size: Long): Long
    private external fun native_kv_restore(context: Long, seqId: Int, lastPos: Int)
    private external fun native_shm_verify(ptr: Long, size: Long, expectedHash: String): Boolean
    private external fun native_inject_token(context: Long, tokenId: Int, kvOffset: Int)
    private external fun native_set_output_shm(fd: Int, size: Int)
    private external fun native_get_embeddings(model: Long, text: String): FloatArray
    private external fun native_cancel_inference()
    private external fun probe_backend(modelPath: String, backendType: Int): Boolean
    private external fun native_process_image(model: Long, context: Long, buffer: HardwareBuffer, width: Int, height: Int): Boolean

    suspend fun probe(modelPath: String, backendType: Int): Boolean = withContext(runLoop) {
        try {
            probe_backend(modelPath, backendType)
        } catch (e: Exception) {
            Log.e(tag, "Native probe crashed", e)
            false
        }
    }

    fun getHardwareStatus(): String = try { get_hardware_status() } catch (e: Exception) { "CPU [Fallback]" }

    fun getModelContextLimit(): Int = when (val state = stateRef.get()) {
        is State.Loaded -> get_model_n_ctx_train(state.model)
        else -> 0
    }

    suspend fun getHeartbeat(): Int = withContext(runLoop) { nativeGetHeartbeat() }

    suspend fun init(filesDir: String) = withContext(runLoop) { backend_init(false, filesDir) }

    suspend fun setOutputShm(fd: Int, size: Int) = withContext(runLoop) {
        // PERINGATAN: Pastikan Anda memanggil parcelFileDescriptor.detachFd() sebelum mengoper 'fd' ke sini
        native_set_output_shm(fd, size)
    }

    fun cancelInference() {
        try {
            native_cancel_inference()
        } catch (e: Exception) {
            Log.e(tag, "Failed to send native cancellation signal", e)
        }
    }

    suspend fun kvCacheClear() = withContext(runLoop) {
        when (val state = stateRef.get()) {
            is State.Loaded -> kv_cache_clear(state.context)
            else -> {}
        }
    }

    suspend fun attachShm(fd: Int, size: Long) = withContext(runLoop) {
        val ptr = native_shm_attach(fd, size)
        if (ptr == 0L) throw IllegalStateException("native_shm_attach failed")
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

    fun setTrimLevel(level: Int) { set_trim_level(level) }

    fun isReady(): Boolean = stateRef.get() is State.Loaded

    /**
     * FIXED: Wrapped with inferenceMutex to prevent Use-After-Free Segfaults.
     */
    suspend fun getEmbeddings(text: String): FloatArray {
        return inferenceMutex.withLock {
            withContext(runLoop) {
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
    }

    suspend fun processImageTensor(buffer: HardwareBuffer, width: Int, height: Int): Boolean {
        return inferenceMutex.withLock {
            withContext(runLoop) {
                val state = stateRef.get()
                if (state !is State.Loaded) {
                    Log.e(tag, "[VISION] processImageTensor called but model is not loaded.")
                    return@withContext false
                }
                try {
                    val result = native_process_image(state.model, state.context, buffer, width, height)
                    Log.i(tag, "[VISION] Processed image tensor. Success: $result")
                    result
                } catch (e: Exception) {
                    Log.e(tag, "[VISION] native_process_image threw: ${e.message}")
                    false
                }
            }
        }
    }

    suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1): String {
        return inferenceMutex.withLock {
            withContext(runLoop) {
                when (val state = stateRef.get()) {
                    is State.Loaded -> bench_model(state.context, state.model, state.batch, pp, tg, pl, nr)
                    else -> throw IllegalStateException("No model loaded")
                }
            }
        }
    }

    suspend fun load(pathToModel: String, nCtx: Int, backendMode: Int = 0, grammar: String? = null, topK: Int = 51, topP: Float = 0.95f, temp: Float = 0.8f, progressCallback: ProgressCallback? = null): Long {
        return inferenceMutex.withLock {
            withContext(runLoop) {
                if (stateRef.get() is State.Loaded) return@withContext 1L
                val model = load_model(pathToModel, backendMode, progressCallback)
                if (model == -2L) { Log.e(tag, "Pollution detected."); return@withContext -2L }
                if (model <= 0L) throw IllegalStateException("load_model failed with code $model")
                try { finalizeLoading(model, pathToModel, nCtx, grammar, topK, topP, temp) }
                catch (e: Exception) { Log.e(tag, "Hardware Incompatibility: ${e.message}"); return@withContext -3L }
                model
            }
        }
    }

    suspend fun loadFromFd(fd: Int, offset: Long, size: Long, nCtx: Int, backendMode: Int = 0, grammar: String? = null, topK: Int = 51, topP: Float = 0.95f, temp: Float = 0.8f, progressCallback: ProgressCallback? = null): Long {
        return inferenceMutex.withLock {
            withContext(runLoop) {
                if (stateRef.get() is State.Loaded) return@withContext 1L
                val model = load_model_from_fd(fd, offset, size, backendMode, progressCallback)
                if (model == -2L) { Log.e(tag, "Pollution detected."); return@withContext -2L }
                if (model <= 0L) throw IllegalStateException("load_model_from_fd failed with code $model")
                try { finalizeLoading(model, "FD://$fd", nCtx, grammar, topK, topP, temp) }
                catch (e: Exception) { Log.e(tag, "Hardware Incompatibility: ${e.message}"); return@withContext -3L }
                model
            }
        }
    }

    private fun finalizeLoading(model: Long, label: String, nCtx: Int, grammar: String?, topK: Int, topP: Float, temp: Float) {
        val trainLimit = get_model_n_ctx_train(model)
        val finalCtx = if (trainLimit > 0 && nCtx > trainLimit) {
            Log.w(tag, "Context $nCtx exceeds limit $trainLimit. Clamping.")
            trainLimit
        } else nCtx

        val context = new_context(model, finalCtx)
        if (context == 0L) throw IllegalStateException("new_context() failed")

        val batchSize = if (nCtx > 2048) 2048 else nCtx
        val batch = new_batch(batchSize, 0, 1)
        if (batch == 0L) throw IllegalStateException("new_batch() failed")

        val sampler = new_sampler(context, grammar, topK, topP, temp)
        if (sampler == 0L) throw IllegalStateException("new_sampler() failed")

        Log.i(tag, "Loaded model successfully: $label")
        nctx = finalCtx  // [v1.1.3-SAR] Cache actual context size for stopLimit clamping
        stateRef.set(State.Loaded(model, context, batch, sampler))
    }

    fun send(message: String, formatChat: Boolean = false, topK: Int = 51, topP: Float = 0.95f, temp: Float = 0.8f): Flow<String> = flow {
        inferenceMutex.withLock {
            when (val state = stateRef.get()) {
                is State.Loaded -> {
                    withContext(runLoop) {
                        try { free_sampler(state.sampler) } catch (e: Exception) { Log.e(tag, "Failed to free old sampler", e) }
                        val newSmpl = new_sampler(state.context, null, topK, topP, temp)
                        stateRef.set(state.copy(sampler = newSmpl))
                    }
                    
                    val newState = stateRef.get() as State.Loaded
                    kv_cache_clear(newState.context)

                    val processed = completion_init(newState.context, newState.batch, message, formatChat, nlen)
                    if (processed <= 0) {
                        emit("Error: Prompt too long (Max ${newState.context})")
                        return@flow
                    }

                    // OPTIMASI: Menggunakan primitif array (jauh lebih cepat untuk JNI)
                    val ncurArray = intArrayOf(processed)
                    // [v1.1.3-SAR] Clamp stop limit to n_ctx.
                    // Native completion_init also clamps n_len internally, but the Kotlin
                    // while-loop must respect the same boundary to avoid driving
                    // completion_loop past the KV cache capacity.
                    val stopLimit = minOf(processed + nlen, nctx)

                    while (ncurArray[0] < stopLimit) {
                        val str = completion_loop(newState.context, newState.batch, newState.sampler, stopLimit, ncurArray)
                        if (str == null) break
                        emit(str)
                    }
                }
                else -> {}
            }
        }
    }.flowOn(runLoop)

    suspend fun unload() {
        inferenceMutex.withLock {
            withContext(runLoop) {
                when (val state = stateRef.get()) {
                    is State.Loaded -> {
                        try {
                            try { free_sampler(state.sampler) } catch (e: Exception) { Log.e(tag, "JNI free_sampler failed", e) }
                            finally {
                                try { free_batch(state.batch) } catch (e: Exception) { Log.e(tag, "JNI free_batch failed", e) }
                                finally {
                                    try { free_context(state.context) } catch (e: Exception) { Log.e(tag, "JNI free_context failed", e) }
                                }
                            }
                        } finally {
                            try { free_model(state.model) } catch (e: Exception) { Log.e(tag, "JNI free_model failed", e) }
                            finally {
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
        if (state is State.Loaded) save_state(state.context, path) else false
    }

    suspend fun loadSession(path: String): Boolean = withContext(runLoop) {
        val state = stateRef.get()
        if (state is State.Loaded) load_state(state.context, path) else false
    }

    companion object {
        init {
            val oldPolicy = android.os.StrictMode.allowThreadDiskReads()
            try {
                System.loadLibrary("llama-android")
            } finally {
                android.os.StrictMode.setThreadPolicy(oldPolicy)
            }
        }

        private sealed interface State {
            data object Idle: State
            data class Loaded(val model: Long, val context: Long, val batch: Long, val sampler: Long): State
        }

        private val _instance: LLamaAndroid = LLamaAndroid()
        fun instance(): LLamaAndroid = _instance
    }
}
