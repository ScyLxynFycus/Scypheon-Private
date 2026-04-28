package com.scypheon.sdk.core.sandbox;

interface ISandboxStatusCallback {
    void onInitializationProgress(float progress);
    void onInitializationResult(boolean success);
    void onHardwareStatusUpdate(String status);
    void onInternalError(String error);
    void onPollutionDetected(long residualBytes);
    void onEmbeddings(in float[] embeddings);
}
