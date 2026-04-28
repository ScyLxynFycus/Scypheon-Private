package com.scypheon.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.scypheon.app.ui.screens.GraphEdge
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GraphViewModel @Inject constructor(
    val physics: GraphPhysicsEngine
) : ViewModel() {

    fun initGraph(data: List<GraphEdge>) {
        physics.start(data)
    }

    override fun onCleared() {
        super.onCleared()
        physics.stop()
    }
}
