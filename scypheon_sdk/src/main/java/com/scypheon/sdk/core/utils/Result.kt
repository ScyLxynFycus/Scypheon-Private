package com.scypheon.sdk.core.utils

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val exception: Exception, val message: String = exception.message ?: "Unknown Error") : Result<Nothing>()
    object Loading : Result<Nothing>()
}
