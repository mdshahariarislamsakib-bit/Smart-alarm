package com.smartalarm.app

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import kotlin.math.abs

class CameraTask(
    private val context: Context,
    private val root: View,
    private val done: () -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var awakeScore = 0
    private var finished = false
    private var cameraProvider: ProcessCameraProvider? = null

    // Smoothed values to avoid flickering
    private var smoothLeftEye = 0.5f
    private var smoothRightEye = 0.5f
    private var smoothSmile = 0f

    // Optimized high-speed ML Kit Face Detector with Face Tracking
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.10f)
            .enableTracking()
            .build()
    )

    fun start() {
        val preview = root.findViewById<PreviewView>(R.id.preview)
        preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        preview.scaleType = PreviewView.ScaleType.FILL_CENTER

        ProcessCameraProvider.getInstance(context).also { future ->
            future.addListener({
                try {
                    val provider = future.get()
                    cameraProvider = provider
                    val p = Preview.Builder()
                        .setTargetResolution(Size(640, 480))
                        .build()
                    p.setSurfaceProvider(preview.surfaceProvider)

                    // Target 640x480 resolution for lightning-fast 60 FPS real-time processing
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()

                    analysis.setAnalyzer(executor) { image -> analyze(image) }

                    provider.unbindAll()
                    if (context is LifecycleOwner) {
                        provider.bindToLifecycle(
                            context,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            p,
                            analysis
                        )
                    }
                } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun analyze(img: ImageProxy) {
        if (finished) {
            img.close()
            return
        }

        try {
            val media = img.image ?: run {
                img.close()
                return
            }
            val rotationDegrees = img.imageInfo.rotationDegrees
            val inputImage = InputImage.fromMediaImage(media, rotationDegrees)

            val imgWidth = if (rotationDegrees == 90 || rotationDegrees == 270) img.height else img.width
            val imgHeight = if (rotationDegrees == 90 || rotationDegrees == 270) img.width else img.height

            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    val face = faces.firstOrNull()
                    processFaceState(face, imgWidth, imgHeight)
                }
                .addOnFailureListener {
                    processFaceState(null, imgWidth, imgHeight)
                }
        } catch (_: Exception) {
        } finally {
            img.close()
        }
    }

    private fun processFaceState(face: Face?, imgWidth: Int, imgHeight: Int) {
        if (finished) return

        var eyesOpen = false
        var isSmiling = false
        var headTurned = false

        if (face != null) {
            val rawLeft = face.leftEyeOpenProbability ?: 0.5f
            val rawRight = face.rightEyeOpenProbability ?: 0.5f
            val rawSmile = face.smilingProbability ?: 0f
            val headYaw = abs(face.headEulerAngleY)

            // Exponential smoothing for stability
            smoothLeftEye += (rawLeft - smoothLeftEye) * 0.50f
            smoothRightEye += (rawRight - smoothRightEye) * 0.50f
            smoothSmile += (rawSmile - smoothSmile) * 0.50f

            val avgEye = (smoothLeftEye + smoothRightEye) / 2f

            // Natural and responsive threshold: open if either eye >= 0.38f or average >= 0.35f
            eyesOpen = smoothLeftEye >= 0.38f || smoothRightEye >= 0.38f || avgEye >= 0.35f
            isSmiling = smoothSmile >= 0.25f
            headTurned = headYaw > 12f

            if (eyesOpen) {
                // Rapidly build up awake score
                awakeScore = (awakeScore + 5).coerceAtMost(100)
                if (isSmiling) {
                    awakeScore = (awakeScore + 4).coerceAtMost(100)
                }
            } else {
                // Gentle penalty for temporary blinks
                awakeScore = (awakeScore - 2).coerceAtLeast(0)
            }
        } else {
            awakeScore = (awakeScore - 2).coerceAtLeast(0)
        }

        val isFullyAwake = eyesOpen

        handler.post {
            val overlay = root.findViewById<FaceOverlayView>(R.id.faceOverlay)
            overlay?.updateFace(face, imgWidth, imgHeight, isFullyAwake, awakeScore, frontCam = true)

            root.findViewById<ProgressBar>(R.id.motionProgress)?.progress = awakeScore
            root.findViewById<TextView>(R.id.motionLabel)?.text =
                "Awake Verification: $awakeScore%"

            val eyeLabel = root.findViewById<TextView>(R.id.eyeStatusLabel)
            if (eyeLabel != null) {
                if (face != null) {
                    if (eyesOpen) {
                        eyeLabel.text = "Eyes: OPEN & ALERT ✓"
                        eyeLabel.setTextColor(Color.rgb(0, 230, 118))
                    } else {
                        eyeLabel.text = "Eyes: CLOSED / DROWSY ⚠️"
                        eyeLabel.setTextColor(Color.rgb(255, 59, 92))
                    }
                } else {
                    eyeLabel.text = "Eyes: Searching for Face…"
                    eyeLabel.setTextColor(Color.rgb(255, 215, 64))
                }
            }

            val smileLabel = root.findViewById<TextView>(R.id.smileLabel)
            if (smileLabel != null) {
                val pct = (smoothSmile * 100).toInt().coerceIn(0, 100)
                smileLabel.text = if (isSmiling) "Smile: $pct% 😊" else "Smile: $pct%"
                smileLabel.setTextColor(if (isSmiling) Color.rgb(0, 230, 118) else Color.rgb(0, 229, 255))
            }

            val headLabel = root.findViewById<TextView>(R.id.headLabel)
            if (headLabel != null) {
                headLabel.text = if (headTurned) "Face & Motion: LOCKED ✓" else "Look at the screen & smile to dismiss"
            }

            if (!finished && awakeScore >= 100) {
                finished = true
                stop()
                done()
            }
        }
    }

    fun stop() {
        try {
            cameraProvider?.unbindAll()
            detector.close()
            executor.shutdown()
        } catch (_: Exception) {}
    }
}
