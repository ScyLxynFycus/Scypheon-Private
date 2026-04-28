package com.scypheon.sdk.core.sandbox;

import com.scypheon.sdk.core.sandbox.ISandboxStatusCallback;

interface IModelSandbox {
    /**
     * Initializes the native LLM engine inside the sandbox.
     * Returns true if successful, false if memory VETO or file failure.
     */
    boolean initializeEngine(String modelPath, int nCtx, boolean useMmap);

    /**
     * Executes asynchronous inference. Results are streamed back via callback.
     */
    void generateResponse(String prompt, ISandboxStatusCallback callback);

    /**
     * Force kills the native engine and frees memory.
     */
    void unloadEngine();

    /**
     * Check if the native heap is currently safe for more allocations.
     */
    boolean isHeapSafe();
}
