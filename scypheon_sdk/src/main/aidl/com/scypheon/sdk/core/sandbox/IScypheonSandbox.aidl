package com.scypheon.sdk.core.sandbox;

import com.scypheon.sdk.core.sandbox.IInferenceCallback;
import com.scypheon.sdk.core.sandbox.ISandboxStatusCallback;
import android.os.ParcelFileDescriptor;

/**
 * Isolated process interface for Scypheon Fallback Engine (SFE).
 * All methods are oneway to prevent UI thread blocking and ANRs.
 */
oneway interface IScypheonSandbox {
    /**
     * Initializes the native backend and sets the persistent data directory.
     * Includes the database key for secure cross-process access.
     */
    void init(String filesDir, in byte[] dbKey);

    /**
     * SAR PHASE 3: Zero-Latency Handoff
     * Allows loading the model from a shared memory file descriptor.
     */
    void loadFromFd(in ParcelFileDescriptor pfd, long offset, long size, int backend, int nCtx, ISandboxStatusCallback callback);

    /**
     * Loads the AI model from the given path in an isolated sandbox.
     * Result reported via ISandboxStatusCallback.
     */
    void load(String modelPath, int backendMode, int nCtx, ISandboxStatusCallback callback);

    /**
     * Triggers asynchronous inference. Tokens are streamed back via IInferenceCallback.
     */
    void send(String prompt, int topK, float topP, float temp, int maxTokens, boolean enableThinking, IInferenceCallback callback);

    /**
     * Enterprise signature (v2): Includes requestId for cross-process tracing.
     */
    void sendWithTracing(String prompt, int topK, float topP, float temp, int maxTokens, boolean enableThinking, String requestId, IInferenceCallback callback);

    /**
     * Health check for sandbox liveness.
     */
    void ping();

    /**
     * Unloads the current model and frees resources.
     */
    void unload();

    /**
     * Retrieves real-time hardware status. Result reported via ISandboxStatusCallback.
     */
    void getHardwareStatus(ISandboxStatusCallback callback);

    /**
     * Checks if the native backend is currently ready. Result reported via ISandboxStatusCallback.
     */
    void isReady(ISandboxStatusCallback callback);

    /**
     * SAR PHASE 2: Checkpoint persistence
     */
    void saveSession(String path, ISandboxStatusCallback callback);
    void loadSession(String path, ISandboxStatusCallback callback);

    /**
     * SAR PHASE 3: Zero-Latency Handoff
     */
    void attachTensorMemory(in ParcelFileDescriptor shmFd, long tensorSize, String modelHash);
    void reportShmHealth(int healthCode); // 0=OK, 1=ENOMEM, 2=CORRUPT
    void nativeKvRestore(int seqId, int lastPos);
    void injectToken(int tokenId, int kvOffset, long sequenceNumber);

    /**
     * MDRS: Reactive memory reclamation. 
     * Triggers KV cache eviction based on system ComponentCallbacks2 trim level.
     */
    void reclaimMemory(int level);

    /**
     * Core Semantic: Generates a vector embedding for the given text using the loaded GGUF model.
     * Result reported via ISandboxStatusCallback.onEmbeddings.
     */
    void getEmbeddings(String text, ISandboxStatusCallback callback);
    
    /**
     * [v1.0.5-SAR] Hardware Specialization.
     * Sets the performance profile (0: Efficiency, 1: Balanced, 2: Turbo).
     * Impacts thread priority and Samsung PerfHint cluster assignment.
     */
    void setPerformanceMode(int mode);

    /**
     * [v1.0.6-SAR] Pre-flight Backend Probe.
     * Tests if a specific backend can handle the model before full load.
     * Prevents UI process death by isolating hardware/driver crash in sandbox.
     */
    void probe(String modelPath, int backendMode, ISandboxStatusCallback callback);
    /**
     * [v1.1.0-SAR] Shared Memory Prompting.
     * Allows passing large prompts via FD to bypass Binder's 1MB transaction limit.
     */
    void sendFromFd(in ParcelFileDescriptor pfd, int length, int topK, float topP, float temp, int maxTokens, boolean enableThinking, IInferenceCallback callback);

    /**
     * [v1.1.0-SAR] Shared Memory Embeddings.
     */
    void getEmbeddingsFromFd(in ParcelFileDescriptor pfd, int length, ISandboxStatusCallback callback);

    /**
     * [v1.0.8-SAR] V.I.I.P Shield Protection.
     * Promotes the sandbox service to a foreground service to prevent LMK termination.
     */
    void promoteToForeground();
}
