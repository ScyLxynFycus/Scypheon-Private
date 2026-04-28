package com.scypheon.sdk.core.sandbox;

interface ISandboxStatusCallback {
    void onTokenReceived(String token);
    void onComplete();
    void onError(String message);
    void onMemoryWarning(String detail);
}
