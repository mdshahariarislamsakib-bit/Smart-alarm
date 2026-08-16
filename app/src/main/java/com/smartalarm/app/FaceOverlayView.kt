package com.smartalarm.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var face: Face? = null
    private var imageWidth = 480
    private var imageHeight = 640
    private var isFrontCamera = true
    private var isAwake = false
    private var awakeScore = 0

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }

    private val bgBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun updateFace(
        detectedFace: Face?,
        imgWidth: Int,
        imgHeight: Int,
        awake: Boolean,
        score: Int,
        frontCam: Boolean = true
    ) {
        this.face = detectedFace
        this.imageWidth = imgWidth
        this.imageHeight = imgHeight
        this.isAwake = awake
        this.awakeScore = score
        this.isFrontCamera = frontCam
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentFace = face ?: return

        val scaleX = width.toFloat() / imageWidth.toFloat()
        val scaleY = height.toFloat() / imageHeight.toFloat()
        val scale = maxOf(scaleX, scaleY)

        val offsetX = (width - imageWidth * scale) / 2f
        val offsetY = (height - imageHeight * scale) / 2f

        fun mapX(x: Float): Float {
            val flippedX = if (isFrontCamera) imageWidth - x else x
            return flippedX * scale + offsetX
        }

        fun mapY(y: Float): Float = y * scale + offsetY

        val bounds = currentFace.boundingBox
        val left = mapX(if (isFrontCamera) bounds.right.toFloat() else bounds.left.toFloat())
        val right = mapX(if (isFrontCamera) bounds.left.toFloat() else bounds.right.toFloat())
        val top = mapY(bounds.top.toFloat())
        val bottom = mapY(bounds.bottom.toFloat())

        val color = if (isAwake) Color.rgb(0, 230, 118) else Color.rgb(255, 59, 92)
        boxPaint.color = color
        cornerPaint.color = color

        val rect = RectF(minOf(left, right), top, maxOf(left, right), bottom)
        canvas.drawRoundRect(rect, 16f, 16f, boxPaint)

        val cornerLen = (rect.width() * 0.18f).coerceIn(24f, 60f)
        // Top-Left Corner
        canvas.drawLine(rect.left, rect.top, rect.left + cornerLen, rect.top, cornerPaint)
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + cornerLen, cornerPaint)
        // Top-Right Corner
        canvas.drawLine(rect.right, rect.top, rect.right - cornerLen, rect.top, cornerPaint)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + cornerLen, cornerPaint)
        // Bottom-Left Corner
        canvas.drawLine(rect.left, rect.bottom, rect.left + cornerLen, rect.bottom, cornerPaint)
        canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - cornerLen, cornerPaint)
        // Bottom-Right Corner
        canvas.drawLine(rect.right, rect.bottom, rect.right - cornerLen, rect.bottom, cornerPaint)
        canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - cornerLen, cornerPaint)

        // Draw Eye Indicators
        val leftEye = currentFace.getLandmark(FaceLandmark.LEFT_EYE)
        val rightEye = currentFace.getLandmark(FaceLandmark.RIGHT_EYE)
        val leftOpen = currentFace.leftEyeOpenProbability ?: -1f
        val rightOpen = currentFace.rightEyeOpenProbability ?: -1f

        fun drawEye(landmark: FaceLandmark?, openProb: Float, label: String) {
            if (landmark == null) return
            val ex = mapX(landmark.position.x)
            val ey = mapY(landmark.position.y)
            val eyeColor = if (openProb >= 0.65f) Color.rgb(0, 230, 118) else Color.rgb(255, 59, 92)
            eyePaint.color = eyeColor
            canvas.drawCircle(ex, ey, 26f, eyePaint)

            val eyeText = if (openProb >= 0f) "$label ${(openProb * 100).toInt()}%" else label
            textPaint.color = eyeColor
            textPaint.textSize = 28f
            canvas.drawText(eyeText, ex - 40f, ey - 32f, textPaint)
        }

        drawEye(leftEye, leftOpen, "L:")
        drawEye(rightEye, rightOpen, "R:")

        // Status Header Badge
        val statusText = if (isAwake) "🟢 AWAKE & EYES OPEN ✓ ($awakeScore%)" else "🔴 ASLEEP / EYES CLOSED ($awakeScore%)"
        textPaint.textSize = 34f
        textPaint.color = Color.WHITE

        val textWidth = textPaint.measureText(statusText)
        val badgeRect = RectF(
            rect.centerX() - textWidth / 2f - 20f,
            rect.top - 58f,
            rect.centerX() + textWidth / 2f + 20f,
            rect.top - 8f
        )
        bgBadgePaint.color = if (isAwake) Color.argb(220, 0, 180, 80) else Color.argb(220, 200, 30, 60)
        canvas.drawRoundRect(badgeRect, 12f, 12f, bgBadgePaint)
        canvas.drawText(statusText, rect.centerX() - textWidth / 2f, rect.top - 18f, textPaint)
    }
}
