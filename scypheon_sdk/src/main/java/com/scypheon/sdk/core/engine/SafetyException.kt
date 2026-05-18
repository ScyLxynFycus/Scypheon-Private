package com.scypheon.sdk.core.engine

/**
 * Enterprise-grade exception for safety violations.
 * Used by WorkflowEngine to halt orchestration when adversarial intent or 
 * high-risk clinical hallucinations are detected.
 */
class SafetyException(
    val reason: String,
    val traceId: String? = null
) : Exception(reason)
