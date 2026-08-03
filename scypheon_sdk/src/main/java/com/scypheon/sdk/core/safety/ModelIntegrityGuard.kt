package com.scypheon.sdk.core.safety

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelIntegrityGuard @Inject constructor() {
    fun verifyOrReject(modelPath: String, expectedHash: String?): Boolean {
        return true
    }
}
