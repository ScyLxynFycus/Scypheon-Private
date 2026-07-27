package com.scypheon.sdk.core.sandbox;

import com.scypheon.sdk.core.sandbox.IInferenceCallback;
import com.scypheon.sdk.core.sandbox.ISandboxStatusCallback;
import android.os.ParcelFileDescriptor;

/**
 * Isolated process interface for Scypheon Fallback Engine (SFE).
 * All methods are oneway to prevent UI thread blocking and ANRs.
 */
interface IScypheonSandbox {
    /**
     * PQC Key Exchange: get public key from Sandbox.
     */
    byte[] getKemPublicKey();

    /**
     * PQC Key Exchange: initialize Sandbox with Kyber-encrypted database key.
     */
    void initWithKem(String filesDir, in byte[] ciphertext, in byte[] encryptedDbKey);

    /**
     * Initializes the native backend and sets the persistent data directory.
     * Includes the database key for secure cross-process access.
     */
    void init(String filesDir, in byte[] dbKey);

    /**
     * SAR PHASE 3: Zero-Latency Handoff
     * Allows loading the model from a shared memory file descriptor.
     */
    oneway void loadFromFd(in ParcelFileDescriptor pfd, long offset, long size, int backend, int nCtx, ISandboxStatusCallback callback);

    /**
     * Loads the AI model from the given path in an isolated sandbox.
     * Result reported via ISandboxStatusCallback.
     */
    oneway void load(String modelPath, int backendMode, int nCtx, ISandboxStatusCallback callback);

    /**
     * Triggers asynchronous inference. Tokens are streamed back via IInferenceCallback.
     */
    oneway void send(String prompt, int topK, float topP, float temp, int maxTokens, boolean enableThinking, IInferenceCallback callback);

    /**
     * Enterprise signature (v2): Includes requestId for cross-process tracing.
     */
    oneway void sendWithTracing(String prompt, int topK, float topP, float temp, int maxTokens, boolean enableThinking, String requestId, IInferenceCallback callback);

    /**
     * Health check for sandbox liveness.
     */
    oneway void ping();

    /**
     * Hard-stop the currently running inference job.
     */
    oneway void cancelInference();

    /**
     * Unloads the current model and frees resources.
     */
    oneway void unload();

    /**
     * Retrieves real-time hardware status. Result reported via ISandboxStatusCallback.
     */
    oneway void getHardwareStatus(ISandboxStatusCallback callback);

    /**
     * Checks if the native backend is currently ready. Result reported via ISandboxStatusCallback.
     */
    oneway void isReady(ISandboxStatusCallback callback);

    /**
     * SAR PHASE 2: Checkpoint persistence
     */
    oneway void saveSession(String path, ISandboxStatusCallback callback);
    oneway void loadSession(String path, ISandboxStatusCallback callback);

    /**
     * SAR PHASE 3: Zero-Latency Handoff
     */
    oneway void attachTensorMemory(in ParcelFileDescriptor shmFd, long tensorSize, String modelHash);
    oneway void reportShmHealth(int healthCode); // 0=OK, 1=ENOMEM, 2=CORRUPT
    oneway void nativeKvRestore(int seqId, int lastPos);
    oneway void injectToken(int tokenId, int kvOffset, long sequenceNumber);

    /**
     * MDRS: Reactive memory reclamation. 
     * Triggers KV cache eviction based on system ComponentCallbacks2 trim level.
     */
    oneway void reclaimMemory(int level);

    /**
     * Core Semantic: Generates a vector embedding for the given text using the loaded GGUF model.
     * Result reported via ISandboxStatusCallback.onEmbeddings.
     */
    oneway void getEmbeddings(String text, ISandboxStatusCallback callback);
    
    /**
     * [v1.0.5-SAR] Hardware Specialization.
     * Sets the performance profile (0: Efficiency, 1: Balanced, 2: Turbo).
     * Impacts thread priority and Samsung PerfHint cluster assignment.
     */
    oneway void setPerformanceMode(int mode);

    /**
     * [v1.0.6-SAR] Pre-flight Backend Probe.
     * Tests if a specific backend can handle the model before full load.
     * Prevents UI process death by isolating hardware/driver crash in sandbox.
     */
    oneway void probe(String modelPath, int backendMode, ISandboxStatusCallback callback);
    /**
     * [v1.1.0-SAR] Shared Memory Prompting.
     * Allows passing large prompts via FD to bypass Binder's 1MB transaction limit.
     */
    oneway void sendFromFd(in ParcelFileDescriptor pfd, int length, int topK, float topP, float temp, int maxTokens, boolean enableThinking, IInferenceCallback callback);

    /**
     * [v1.1.0-SAR] Shared Memory Embeddings.
     */
    oneway void getEmbeddingsFromFd(in ParcelFileDescriptor pfd, int length, ISandboxStatusCallback callback);

    /**
     * [v1.0.8-SAR] V.I.I.P Shield Protection.
     * Promotes the sandbox service to a foreground service to prevent LMK termination.
     */
    oneway void promoteToForeground();

    /**
     * [v1.2.0-SAR] Zero-Copy Multimodal Tensor Processing
     * Passes a HardwareBuffer containing raw pixels to the C++ sandbox via NDK AHardwareBuffer.
     * Bypasses the 1MB Binder limit and achieves zero-copy inference for LLaVa/Gemma.
     */
    oneway void processImageTensor(in android.hardware.HardwareBuffer buffer, int width, int height, IInferenceCallback callback);
}
