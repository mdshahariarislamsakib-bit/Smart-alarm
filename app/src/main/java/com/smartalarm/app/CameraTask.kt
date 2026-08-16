package com.smartalarm.app

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
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
    private var last: ByteArray? = null
    private var awakeScore = 0
    private var finished = false
    private var cameraProvider: ProcessCameraProvider? = null

    // High-performance real-time face, eye, and landmark detection powered by ML Kit
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
    )

    fun start() {
        val preview = root.findViewById<PreviewView>(R.id.preview)

        ProcessCameraProvider.getInstance(context).also { future ->
            future.addListener({
                try {
                    val provider = future.get()
                    cameraProvider = provider
                    val p = Preview.Builder().build()
                    p.setSurfaceProvider(preview.surfaceProvider)

                    val analysis = ImageAnalysis.Builder()
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
            val media = img.image ?: return
            val rotationDegrees = img.imageInfo.rotationDegrees
            val inputImage = InputImage.fromMediaImage(media, rotationDegrees)

            // Account for sensor rotation in overlay dimensions
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
        var leftProb = 0f
        var rightProb = 0f
        var smileProb = 0f
        var headTurned = false

        if (face != null) {
            leftProb = face.leftEyeOpenProbability ?: 0f
            rightProb = face.rightEyeOpenProbability ?: 0f
            smileProb = face.smilingProbability ?: 0f
            val headYaw = abs(face.headEulerAngleY)

            // Eyes are considered fully open if both probabilities exceed threshold
            eyesOpen = leftProb >= 0.65f && rightProb >= 0.65f
            isSmiling = smileProb >= 0.45f
            headTurned = headYaw > 15f

            if (eyesOpen) {
                // Rapidly increase awake score when eyes are fully open
                awakeScore = (awakeScore + 4).coerceAtMost(100)
                if (isSmiling) {
                    awakeScore = (awakeScore + 2).coerceAtMost(100)
                }
            } else {
                // Drop awake score if eyes are closed or half-closed
                awakeScore = (awakeScore - 6).coerceAtLeast(0)
            }
        } else {
            // No face detected, slowly drop score
            awakeScore = (awakeScore - 3).coerceAtLeast(0)
        }

        val isFullyAwake = eyesOpen

        handler.post {
            // Update Canvas Overlay
            val overlay = root.findViewById<FaceOverlayView>(R.id.faceOverlay)
            overlay?.updateFace(face, imgWidth, imgHeight, isFullyAwake, awakeScore, frontCam = true)

            // Update Progress Bar
            root.findViewById<ProgressBar>(R.id.motionProgress)?.progress = awakeScore
            root.findViewById<TextView>(R.id.motionLabel)?.text =
                "Awake Verification: $awakeScore%"

            // Update Eye Status Text
            val eyeLabel = root.findViewById<TextView>(R.id.eyeStatusLabel)
            if (eyeLabel != null) {
                if (face != null) {
                    if (eyesOpen) {
                        eyeLabel.text = "Eyes: OPEN & ALERT ✓"
                        eyeLabel.setTextColor(Color.rgb(0, 230, 118)) // Green
                    } else {
                        eyeLabel.text = "Eyes: CLOSED / HALF-CLOSED ⚠️"
                        eyeLabel.setTextColor(Color.rgb(255, 59, 92)) // Red
                    }
                } else {
                    eyeLabel.text = "Eyes: No Face Detected"
                    eyeLabel.setTextColor(Color.rgb(255, 59, 92))
                }
            }

            // Update Smile Text
            val smileLabel = root.findViewById<TextView>(R.id.smileLabel)
            if (smileLabel != null) {
                val pct = (smileProb * 100).toInt()
                smileLabel.text = if (isSmiling) "Smile: $pct% 😊" else "Smile: $pct%"
                smileLabel.setTextColor(if (isSmiling) Color.rgb(0, 230, 118) else Color.rgb(0, 229, 255))
            }

            // Update Head / Motion Text
            val headLabel = root.findViewById<TextView>(R.id.headLabel)
            if (headLabel != null) {
                headLabel.text = if (headTurned) "Head Movement: VERIFIED ✓" else "Keep eyes open & hold phone up"
            }

            // Verification Complete Trigger
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
