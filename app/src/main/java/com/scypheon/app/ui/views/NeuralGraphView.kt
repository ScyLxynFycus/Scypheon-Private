package com.scypheon.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.util.LongSparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.scypheon.app.data.models.GraphLayout
import com.scypheon.app.data.models.GraphNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class NeuralGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
    }
    
    // --- VIEWPORT MATRIX ENGINE ---
    private val viewportMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private val touchPoint = FloatArray(2)
    private var scale = 1f
    private var panX = 0f
    private var panY = 0f

    private var layoutRef: GraphLayout? = null
    private val spatialIndex = MutableGridSpatialIndex(cellSize = 150f)
    private var isAnimating = false
    private var isDirty = false
    private var animateParticles = false
    private var tick = 0f
    
    private var onNodeClickListener: ((GraphNode) -> Unit)? = null
    private var physicsJob: Job? = null

    fun updateTransform(newScale: Float, newPanX: Float, newPanY: Float) {
        scale = newScale
        panX = newPanX
        panY = newPanY
        
        viewportMatrix.reset()
        viewportMatrix.postTranslate(panX, panY)
        viewportMatrix.postScale(scale, scale, width / 2f, height / 2f)
        viewportMatrix.invert(inverseMatrix)
        
        isDirty = true
        if (!isAnimating) {
            isAnimating = true
            postInvalidateOnAnimation()
        }
    }

    fun bindPhysics(flow: StateFlow<GraphLayout>, scope: CoroutineScope) {
        physicsJob?.cancel()
        physicsJob = flow.onEach { updateLayout(it) }.launchIn(scope)
    }

    fun unbindPhysics() {
        physicsJob?.cancel()
        physicsJob = null
    }

    private fun updateLayout(layout: GraphLayout) {
        layoutRef = layout
        spatialIndex.rebuild(layout.nodes)
        isDirty = true
        animateParticles = layout.nodes.size <= 150
        if (!isAnimating) {
            isAnimating = true
            postInvalidateOnAnimation()
        }
    }

    fun setLifecycle(lifecycle: Lifecycle) {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isAnimating = true
                postInvalidateOnAnimation()
            }
            override fun onStop(owner: LifecycleOwner) {
                isAnimating = false
            }
        })
    }

    override fun onDraw(canvas: Canvas) {
        val layout = layoutRef ?: return
        
        canvas.save()
        canvas.concat(viewportMatrix) // Apply viewport transformation

        if (animateParticles) tick = (tick + 0.01f) % 1f

        // 1. Draw Edges
        paint.strokeWidth = 1.2f / scale
        layout.edges.forEach { edge ->
            paint.color = 0x22888888.toInt()
            canvas.drawLine(edge.fromX, edge.fromY, edge.toX, edge.toY, paint)
            
            if (animateParticles) {
                val t = (tick + edge.particleOffset) % 1f
                val px = edge.fromX + (edge.toX - edge.fromX) * t
                val py = edge.fromY + (edge.toY - edge.fromY) * t
                paint.color = 0x8000E676.toInt()
                canvas.drawCircle(px, py, 2.5f / scale, paint)
            }
        }

        // 2. Draw Nodes
        layout.nodes.forEach { node ->
            paint.color = (node.colorArgb and 0x00FFFFFF) or 0x1A000000
            canvas.drawCircle(node.posX, node.posY, node.radius * 2f, paint)
            
            paint.color = node.colorArgb
            canvas.drawCircle(node.posX, node.posY, node.radius, paint)
            
            if (layout.nodes.size <= 80 || node.isSelected) {
                canvas.drawText(node.label, node.posX, node.posY + node.radius + 20f, textPaint)
            }
        }

        canvas.restore()

        if (animateParticles || isDirty) {
            isDirty = false
            postInvalidateOnAnimation()
        } else {
            isAnimating = false
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            // [HARDENING] Inverse Mapping: Screen -> World coordinates
            touchPoint[0] = event.x
            touchPoint[1] = event.y
            inverseMatrix.mapPoints(touchPoint)
            
            val hit = spatialIndex.query(touchPoint[0], touchPoint[1], 40f / scale)
            if (hit != null) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onNodeClickListener?.invoke(hit)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun setOnNodeClickListener(l: (GraphNode) -> Unit) {
        onNodeClickListener = l
    }
}

class MutableGridSpatialIndex(private val cellSize: Float) {
    private val grid = LongSparseArray<MutableList<GraphNode>>()
    private val bucketPool = Array(256) { mutableListOf<GraphNode>() }
    private var poolPtr = 0

    fun rebuild(nodes: List<GraphNode>) {
        for (i in 0 until grid.size()) {
            val bucket = grid.valueAt(i)
            bucket.clear()
            if (poolPtr < bucketPool.size) bucketPool[poolPtr++] = bucket
        }
        grid.clear()
        poolPtr = 0

        nodes.forEach { node ->
            val cx = (node.posX / cellSize).toInt()
            val cy = (node.posY / cellSize).toInt()
            val key = ((cx.toLong() and 0xFFFFFFFFL) shl 32) or (cy.toLong() and 0xFFFFFFFFL)
            
            val bucket = grid.get(key) ?: run {
                if (poolPtr > 0) bucketPool[--poolPtr] else mutableListOf<GraphNode>()
            }
            bucket.add(node)
            grid.put(key, bucket)
        }
    }

    fun query(x: Float, y: Float, radius: Float): GraphNode? {
        val cx = (x / cellSize).toInt()
        val cy = (y / cellSize).toInt()
        val key = ((cx.toLong() and 0xFFFFFFFFL) shl 32) or (cy.toLong() and 0xFFFFFFFFL)
        val candidates = grid.get(key) ?: return null
        
        var closest: GraphNode? = null
        var minDist = Float.MAX_VALUE
        val rSq = (radius + 20f) * (radius + 20f)
        for (node in candidates) {
            val dx = node.posX - x
            val dy = node.posY - y
            val dSq = dx * dx + dy * dy
            if (dSq < rSq && dSq < minDist) {
                minDist = dSq
                closest = node
            }
        }
        return closest
    }
}
