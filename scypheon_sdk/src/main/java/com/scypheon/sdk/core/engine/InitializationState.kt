package com.scypheon.sdk.core.engine

sealed class InitializationState {
    data object Idle : InitializationState()
    data class Analyzing(val step: String) : InitializationState()
    data class Trying(val backend: String, val attempt: Int) : InitializationState()
    data class Loading(val backend: String, val progress: Float) : InitializationState()
    data object Attaching : InitializationState()
    data class Failed(val backend: String, val error: String) : InitializationState()
    data class Success(val hardware: String) : InitializationState()
    data class Degraded(val hardware: String, val warning: String) : InitializationState()
}
