package com.scypheon.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.scypheon.app.data.models.RawGraphEdge
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope

@HiltViewModel
class GraphViewModel @Inject constructor() : ViewModel() {

    val physics = GraphPhysicsEngine(viewModelScope)

    fun initGraph(data: List<RawGraphEdge>) {
        physics.start(data)
    }

    override fun onCleared() {
        super.onCleared()
        physics.stop()
    }
}
