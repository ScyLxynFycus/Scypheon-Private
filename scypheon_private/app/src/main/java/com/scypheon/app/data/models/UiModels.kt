package com.scypheon.app.data.models

data class SystemHealth(
    val ramUsedMb: Long = 0L, 
    val ramTotalMb: Long = 0L, 
    val isLowMemory: Boolean = false,
    val isEliteOk: Boolean = false,
    val isUniversalOk: Boolean = false,
    val isMemoryOk: Boolean = false,
    val isPiggybacking: Boolean = false,
    val memoryPath: String = "",
    val elitePath: String = "",
    val universalPath: String = "",
    val modelName: String = "",
    val backend: String = "",
    val requiredGB: Double = 0.0,
    val availableGB: Double = 0.0
)

data class OomDiagnostic(
    val message: String = "", 
    val ramMb: Long = 0L,
    val backend: String = "",
    val modelName: String = "",
    val requiredGB: Double = 0.0,
    val availableGB: Double = 0.0
)
