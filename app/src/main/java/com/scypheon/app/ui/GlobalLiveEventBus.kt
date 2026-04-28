package com.scypheon.app.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Lightweight event bus to route background "Live" agent alerts (like Deaf Environment Guardian)
 * directly into the MainChatScreen UI.
 */
object GlobalLiveEventBus {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val events = _events.asSharedFlow()

    fun postEvent(message: String) {
        _events.tryEmit(message)
    }
}
