package com.scypheon.sdk.core.sandbox;

/**
 * IInferenceCallback: The High-Fidelity IPC Telemetry Bridge.
 * Engineered for zero-latency SharedMemory synchronization and granular lifecycle tracking.
 */
oneway interface IInferenceCallback {
    
    // --------------------------------------------------------------------
    // PHASE 1: MEMORY & LIFECYCLE HANDSHAKE
    // --------------------------------------------------------------------
    
    /**
     * [SAR PHASE 2] Ashmem / SharedMemory IPC
     * Notifies the client that a shared memory region is ready for reading tokens.
     * @param pfd  The ParcelFileDescriptor mapping to the C++ memory space.
     * @param size The bounded size of the memory region.
     */
    void onOutputSharedMemoryReady(in android.os.ParcelFileDescriptor pfd, int size);

    /**
     * Indicates a shift in the inference pipeline state.
     * Critical for dynamic UI rendering (e.g., "Thinking..." vs "Typing...").
     * 
     * @param phase 0 = PREFILL (Evaluating Prompt/RAG), 1 = DECODING (Generating Tokens)
     */
    void onPhaseChanged(int phase);

    // --------------------------------------------------------------------
    // PHASE 2: EXECUTION STREAM
    // --------------------------------------------------------------------

    /**
     * Signals that N tokens are available in the SHM buffer.
     * The Main Process reads exactly (count - lastTokenCount) tokens from the offset.
     */
    void onTokenAvailable(int count);

    // --------------------------------------------------------------------
    // PHASE 3: RESOLUTION & TELEMETRY
    // --------------------------------------------------------------------

    /**
     * Called when inference completes naturally.
     * Reports critical telemetry data for Kaggle judging and performance monitoring.
     * 
     * @param promptTokens  Number of tokens in the prompt/RAG context.
     * @param genTokens     Number of tokens generated.
     * @param ttftMs        Time To First Token (milliseconds).
     * @param tps           Tokens Per Second during the decode phase.
     */
    void onComplete(int promptTokens, int genTokens, long ttftMs, float tps);

    /**
     * Granular error reporting for exact Circuit Breaker recovery strategies.
     * 
     * @param errorCode 100 = OOM, 101 = CONTEXT_EXCEEDED, 102 = NATIVE_SEGFAULT, 103 = TIMEOUT
     * @param message   Human-readable debug trace.
     */
    void onError(int errorCode, String message);
}
