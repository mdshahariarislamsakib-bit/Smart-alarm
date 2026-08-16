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

    // Smooth interpolated drawing coordinates
    private var smoothLeft = 0f
    private var smoothRight = 0f
    private var smoothTop = 0f
    private var smoothBottom = 0f
    private var hasTarget = false

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 9f
        strokeCap = Paint.Cap.ROUND
    }

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(8f, 0f, 0f, Color.BLACK)
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
        if (imgWidth > 0 && imgHeight > 0) {
            this.imageWidth = imgWidth
            this.imageHeight = imgHeight
        }
        this.isAwake = awake
        this.awakeScore = score
        this.isFrontCamera = frontCam
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentFace = face ?: run {
            hasTarget = false
            return
        }

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) return

        val scale = maxOf(viewWidth / imageWidth.toFloat(), viewHeight / imageHeight.toFloat())
        val postScaleWidth = imageWidth * scale
        val postScaleHeight = imageHeight * scale
        val dx = (viewWidth - postScaleWidth) / 2f
        val dy = (viewHeight - postScaleHeight) / 2f

        fun mapX(x: Float): Float {
            val flipped = if (isFrontCamera) (imageWidth - x) else x
            return flipped * scale + dx
        }

        fun mapY(y: Float): Float = y * scale + dy

        val bounds = currentFace.boundingBox
        val targetLeft = mapX(if (isFrontCamera) bounds.right.toFloat() else bounds.left.toFloat())
        val targetRight = mapX(if (isFrontCamera) bounds.left.toFloat() else bounds.right.toFloat())
        val targetTop = mapY(bounds.top.toFloat())
        val targetBottom = mapY(bounds.bottom.toFloat())

        val minX = minOf(targetLeft, targetRight)
        val maxX = maxOf(targetLeft, targetRight)

        // Smooth Exponential Moving Average for box position
        if (!hasTarget) {
            smoothLeft = minX
            smoothRight = maxX
            smoothTop = targetTop
            smoothBottom = targetBottom
            hasTarget = true
        } else {
            smoothLeft += (minX - smoothLeft) * 0.45f
            smoothRight += (maxX - smoothRight) * 0.45f
            smoothTop += (targetTop - smoothTop) * 0.45f
            smoothBottom += (targetBottom - smoothBottom) * 0.45f
        }

        val primaryColor = if (isAwake) Color.rgb(0, 230, 118) else Color.rgb(255, 59, 92)
        boxPaint.color = primaryColor
        cornerPaint.color = primaryColor

        val faceRect = RectF(smoothLeft, smoothTop, smoothRight, smoothBottom)

        // Subtle glowing background inside face box
        fillPaint.color = if (isAwake) Color.argb(25, 0, 230, 118) else Color.argb(25, 255, 59, 92)
        canvas.drawRoundRect(faceRect, 18f, 18f, fillPaint)
        canvas.drawRoundRect(faceRect, 18f, 18f, boxPaint)

        // Corner Accent Brackets
        val cornerLen = (faceRect.width() * 0.20f).coerceIn(28f, 65f)
        canvas.drawLine(faceRect.left, faceRect.top, faceRect.left + cornerLen, faceRect.top, cornerPaint)
        canvas.drawLine(faceRect.left, faceRect.top, faceRect.left, faceRect.top + cornerLen, cornerPaint)

        canvas.drawLine(faceRect.right, faceRect.top, faceRect.right - cornerLen, faceRect.top, cornerPaint)
        canvas.drawLine(faceRect.right, faceRect.top, faceRect.right, faceRect.top + cornerLen, cornerPaint)

        canvas.drawLine(faceRect.left, faceRect.bottom, faceRect.left + cornerLen, faceRect.bottom, cornerPaint)
        canvas.drawLine(faceRect.left, faceRect.bottom, faceRect.left, faceRect.bottom - cornerLen, cornerPaint)

        canvas.drawLine(faceRect.right, faceRect.bottom, faceRect.right - cornerLen, faceRect.bottom, cornerPaint)
        canvas.drawLine(faceRect.right, faceRect.bottom, faceRect.right, faceRect.bottom - cornerLen, cornerPaint)

        // Draw Eye Indicators
        val leftEye = currentFace.getLandmark(FaceLandmark.LEFT_EYE)
        val rightEye = currentFace.getLandmark(FaceLandmark.RIGHT_EYE)
        val leftOpen = currentFace.leftEyeOpenProbability ?: 0.5f
        val rightOpen = currentFace.rightEyeOpenProbability ?: 0.5f

        fun drawEye(landmark: FaceLandmark?, openProb: Float, label: String) {
            if (landmark == null) return
            val ex = mapX(landmark.position.x)
            val ey = mapY(landmark.position.y)
            val isOpen = openProb >= 0.40f
            val eyeColor = if (isOpen) Color.rgb(0, 230, 118) else Color.rgb(255, 59, 92)
            eyePaint.color = eyeColor

            // Outer reticle circle
            canvas.drawCircle(ex, ey, 24f, eyePaint)
            fillPaint.color = if (isOpen) Color.argb(70, 0, 230, 118) else Color.argb(70, 255, 59, 92)
            canvas.drawCircle(ex, ey, 14f, fillPaint)

            // Text tag
            val pct = (openProb * 100).toInt().coerceIn(0, 100)
            val tag = "$label $pct%"
            textPaint.color = eyeColor
            textPaint.textSize = 26f
            canvas.drawText(tag, ex - 32f, ey - 30f, textPaint)
        }

        drawEye(leftEye, leftOpen, "L:")
        drawEye(rightEye, rightOpen, "R:")

        // Status Header Badge
        val statusText = if (isAwake) "🟢 AWAKE & ALERT ✓ ($awakeScore%)" else "🔴 EYES CLOSED / DROWSY ($awakeScore%)"
        textPaint.textSize = 32f
        textPaint.color = Color.WHITE

        val textWidth = textPaint.measureText(statusText)
        val badgeRect = RectF(
            faceRect.centerX() - textWidth / 2f - 24f,
            faceRect.top - 62f,
            faceRect.centerX() + textWidth / 2f + 24f,
            faceRect.top - 10f
        )
        badgePaint.color = if (isAwake) Color.argb(230, 0, 170, 75) else Color.argb(230, 210, 30, 60)
        canvas.drawRoundRect(badgeRect, 14f, 14f, badgePaint)
        canvas.drawText(statusText, faceRect.centerX() - textWidth / 2f, faceRect.top - 22f, textPaint)
    }
}
