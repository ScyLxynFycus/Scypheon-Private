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
import kotlin.math.sin
import kotlin.math.cos

class NeuralGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    
    private fun dpToPx(dp: Float): Float = dp * context.resources.displayMetrics.density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // [v1.5.0-SAR] Premium light theme: refined dark text with subtle shadow for depth
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dpToPx(11f)
        color = 0xFF3C3C43.toInt() // iOS secondary label color
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
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
    private var isLifecycleActive = true
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
        postInvalidateOnAnimation()
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
        postInvalidateOnAnimation()
    }

    fun setLifecycle(lifecycle: Lifecycle) {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isLifecycleActive = true
                postInvalidateOnAnimation()
            }
            override fun onStop(owner: LifecycleOwner) {
                isLifecycleActive = false
            }
        })
    }

    override fun onDraw(canvas: Canvas) {
        val layout = layoutRef ?: return
        
        canvas.save()
        canvas.concat(viewportMatrix) // Apply viewport transformation

        if (animateParticles) tick = (tick + 0.0015f) % 1f // Significantly slowed down for a dreamy, fluid, and buttery smooth premium aesthetic

        // [v1.5.0-SAR] Premium light theme rendering
        
        // 1. Draw Edges — crisp, glowing neural connection lines
        layout.edges.forEach { edge ->
            // Base Edge Line - thick, visible semi-transparent premium blue
            paint.strokeWidth = dpToPx(1.5f)
            paint.color = 0x333478F6.toInt() // Vibrant iOS blue at 20% opacity
            paint.style = Paint.Style.STROKE
            canvas.drawLine(edge.fromX, edge.fromY, edge.toX, edge.toY, paint)
            
            // Draw flowing light pulse particles
            if (animateParticles) {
                val t = (tick + edge.particleOffset) % 1f
                val px = edge.fromX + (edge.toX - edge.fromX) * t
                val py = edge.fromY + (edge.toY - edge.fromY) * t
                
                paint.style = Paint.Style.FILL
                // Inner high-intensity particle core
                paint.color = 0xCC3478F6.toInt()
                canvas.drawCircle(px, py, dpToPx(3.2f), paint)
                
                // Outer glowing halo
                paint.color = 0x253478F6.toInt()
                canvas.drawCircle(px, py, dpToPx(7.5f), paint)
            }
            
            // Draw Predicate Text Label along the edge when zoomed in
            if (scale >= 0.85f && edge.predicate.isNotEmpty()) {
                val midX = (edge.fromX + edge.toX) / 2f
                val midY = (edge.fromY + edge.toY) / 2f
                
                val predPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = dpToPx(8.5f)
                    color = 0xAA5F6368.toInt() // Slate grey
                    textAlign = Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.ITALIC)
                }
                
                // Calculate angle of edge for text rotation
                val dx = edge.toX - edge.fromX
                val dy = edge.toY - edge.fromY
                var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (angle > 90 || angle < -90) {
                    angle += 180f
                }
                
                canvas.save()
                canvas.translate(midX, midY)
                canvas.rotate(angle)
                
                val text = edge.predicate
                val textWidth = predPaint.measureText(text)
                
                val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xE6FFFFFF.toInt() // capsule fill
                    style = Paint.Style.FILL
                }
                val capBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x14000000.toInt() // subtle border
                    style = Paint.Style.STROKE
                    strokeWidth = dpToPx(0.8f)
                }
                
                val paddingX = dpToPx(4.5f)
                val paddingY = dpToPx(1.8f)
                val rLeft = -textWidth / 2f - paddingX
                val rTop = predPaint.fontMetrics.ascent - paddingY
                val rRight = textWidth / 2f + paddingX
                val rBottom = predPaint.fontMetrics.descent + paddingY
                
                canvas.drawRoundRect(rLeft, rTop, rRight, rBottom, dpToPx(4f), dpToPx(4f), capPaint)
                canvas.drawRoundRect(rLeft, rTop, rRight, rBottom, dpToPx(4f), dpToPx(4f), capBorderPaint)
                
                canvas.drawText(text, 0f, - (predPaint.fontMetrics.ascent + predPaint.fontMetrics.descent) / 2f, predPaint)
                canvas.restore()
            }
        }

        // 2. Draw Nodes — Apple-like glassmorphic 3D spheres with pulsing halos
        layout.nodes.forEach { node ->
            val nodeColor = node.colorArgb
            
            // Soft colored ambient shadow
            paint.style = Paint.Style.FILL
            paint.color = (nodeColor and 0x00FFFFFF) or 0x1A000000
            canvas.drawCircle(node.posX, node.posY, node.radius * 2.2f, paint)
            
            // Selected node dynamic pulsing ring
            if (node.isSelected) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dpToPx(2.5f)
                val pulseAlpha = (0.5f + 0.4f * sin(tick * 2f * Math.PI.toFloat())).coerceIn(0f, 1f)
                val alphaInt = (pulseAlpha * 255).toInt()
                paint.color = (nodeColor and 0x00FFFFFF) or (alphaInt shl 24)
                val ringRadius = node.radius * (1.1f + 0.15f * (1f + sin(tick * 2f * Math.PI.toFloat())))
                canvas.drawCircle(node.posX, node.posY, ringRadius, paint)
            }
            
            // Core node sphere base
            paint.style = Paint.Style.FILL
            paint.color = nodeColor
            canvas.drawCircle(node.posX, node.posY, node.radius, paint)
            
            // Subtle 3D inner shadow overlay
            paint.color = 0x22000000 // 13% black shadow
            canvas.drawCircle(node.posX + node.radius * 0.1f, node.posY + node.radius * 0.1f, node.radius * 0.9f, paint)
            
            // White highlight glint on top-left (3D glass dome effect)
            paint.color = 0xAAFFFFFF.toInt()
            canvas.drawCircle(node.posX - node.radius * 0.25f, node.posY - node.radius * 0.25f, node.radius * 0.28f, paint)
            
            // Draw clean capsule label
            if (layout.nodes.size <= 80 || node.isSelected) {
                val text = node.label
                val textWidth = textPaint.measureText(text)
                
                val paddingX = dpToPx(8f)
                val paddingY = dpToPx(4f)
                val rectLeft = node.posX - textWidth / 2f - paddingX
                val rectTop = node.posY + node.radius + dpToPx(12f) + textPaint.fontMetrics.ascent - paddingY
                val rectRight = node.posX + textWidth / 2f + paddingX
                val rectBottom = node.posY + node.radius + dpToPx(12f) + textPaint.fontMetrics.descent + paddingY
                
                // Capsule white fill with shadow
                val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xF5FFFFFF.toInt() // 96% white
                    style = Paint.Style.FILL
                    setShadowLayer(4f, 0f, 2f, 0x1A000000.toInt())
                }
                
                val capBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (node.isSelected) nodeColor else 0x1A000000.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = dpToPx(if (node.isSelected) 1.2f else 0.8f)
                }
                
                canvas.drawRoundRect(rectLeft, rectTop, rectRight, rectBottom, dpToPx(8f), dpToPx(8f), capPaint)
                canvas.drawRoundRect(rectLeft, rectTop, rectRight, rectBottom, dpToPx(8f), dpToPx(8f), capBorderPaint)
                
                canvas.drawText(
                    text,
                    node.posX,
                    node.posY + node.radius + dpToPx(12f) - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f,
                    textPaint
                )
            }
        }

        canvas.restore()

        if (isLifecycleActive && (animateParticles || isDirty)) {
            isDirty = false
            postInvalidateOnAnimation()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        isDirty = true
        postInvalidateOnAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isDirty = true
        postInvalidateOnAnimation()
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
