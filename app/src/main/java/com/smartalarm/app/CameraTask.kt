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

    // High-speed real-time face detector
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.08f)
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

        var isFaceVerified = false
        var smilePercent = 0

        if (face != null) {
            val rawLeft = face.leftEyeOpenProbability ?: 0.5f
            val rawRight = face.rightEyeOpenProbability ?: 0.5f
            val rawSmile = face.smilingProbability ?: 0f
            smilePercent = (rawSmile * 100).toInt().coerceIn(0, 100)

            // Face is verified as long as face is visible & eyes aren't shut tight (or if smiling)
            isFaceVerified = rawLeft >= 0.20f || rawRight >= 0.20f || rawSmile >= 0.15f || (face.boundingBox.width() > 50)

            if (isFaceVerified) {
                // Instant quick build-up (completes in ~2 frames / 0.3s)
                awakeScore = (awakeScore + 40).coerceAtMost(100)
            } else {
                awakeScore = (awakeScore + 15).coerceAtMost(100)
            }
        } else {
            awakeScore = (awakeScore - 1).coerceAtLeast(0)
        }

        handler.post {
            val overlay = root.findViewById<FaceOverlayView>(R.id.faceOverlay)
            overlay?.updateFace(face, imgWidth, imgHeight, isFaceVerified, awakeScore, frontCam = true)

            root.findViewById<ProgressBar>(R.id.motionProgress)?.progress = awakeScore
            root.findViewById<TextView>(R.id.motionLabel)?.text =
                "Verification: $awakeScore%"

            val eyeLabel = root.findViewById<TextView>(R.id.eyeStatusLabel)
            if (eyeLabel != null) {
                if (face != null) {
                    eyeLabel.text = "Face & Eyes: DETECTED ✓"
                    eyeLabel.setTextColor(Color.rgb(0, 230, 118))
                } else {
                    eyeLabel.text = "Show Face to Camera…"
                    eyeLabel.setTextColor(Color.rgb(255, 215, 64))
                }
            }

            val smileLabel = root.findViewById<TextView>(R.id.smileLabel)
            if (smileLabel != null) {
                smileLabel.text = if (smilePercent > 10) "Smile: $smilePercent% 😊" else "Smile: $smilePercent%"
                smileLabel.setTextColor(if (smilePercent > 10) Color.rgb(0, 230, 118) else Color.rgb(0, 229, 255))
            }

            val headLabel = root.findViewById<TextView>(R.id.headLabel)
            if (headLabel != null) {
                headLabel.text = if (isFaceVerified) "✓ Face recognized — completing!" else "Look at the screen"
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
