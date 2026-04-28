package com.scypheon.sdk.core.gateway

import android.graphics.Bitmap

/**
 * Common data structure for VLM (Vision-Language Model) queries.
 */
data class MultimodalRequest(
    val prompt: String,
    val image: Bitmap? = null,
    val systemInstruction: String? = null
)
