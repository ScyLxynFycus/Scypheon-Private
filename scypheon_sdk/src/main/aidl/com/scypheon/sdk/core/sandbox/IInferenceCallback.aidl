package com.scypheon.sdk.core.sandbox;

oneway interface IInferenceCallback {
    /**
     * Called when a batch of tokens is generated.
     * Batching reduces IPC overhead for high-speed inference.
     */
    void onTokens(in List<String> tokens);
    
    /**
     * Called when a critical error occurs in the sandbox.
     */
    void onError(String message);
    
    /**
     * Called when inference is naturally complete.
     */
    void onComplete();
}
