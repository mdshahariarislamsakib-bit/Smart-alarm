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
    private var motion = 0
    private var turned = false
    private var finished = false
    private var cameraProvider: ProcessCameraProvider? = null

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
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
                    p.surfaceProvider = preview.surfaceProvider

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
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
            val plane = img.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val n = minOf(buffer.remaining(), 5000)
            val current = ByteArray(n)
            buffer.get(current)

            val old = last
            if (old != null && old.size == current.size) {
                var total = 0L
                for (i in current.indices step 20) {
                    total += abs(
                        (current[i].toInt() and 255) -
                            (old[i].toInt() and 255)
                    )
                }
                val avg = total / (current.size / 20).coerceAtLeast(1)
                if (avg > 10) motion = (motion + 2).coerceAtMost(100)
            }
            last = current

            img.image?.let { media ->
                detector.process(
                    InputImage.fromMediaImage(
                        media,
                        img.imageInfo.rotationDegrees
                    )
                ).addOnSuccessListener { faces ->
                    val face = faces.firstOrNull()
                    if (face != null && abs(face.headEulerAngleY) > 18f) turned = true

                    handler.post {
                        root.findViewById<ProgressBar>(R.id.motionProgress)?.progress = motion
                        root.findViewById<TextView>(R.id.motionLabel)?.text =
                            "Awake Motion: $motion%"

                        val head = root.findViewById<TextView>(R.id.headLabel)
                        if (head != null) {
                            head.text =
                                if (turned) "Head turn: VERIFIED ✓"
                                else "Head turn: NOT VERIFIED"
                            head.setTextColor(
                                if (turned) Color.rgb(0, 230, 118)
                                else Color.RED
                            )
                        }

                        if (!finished && motion >= 100 && turned) {
                            finished = true
                            stop()
                            done()
                        }
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            img.close()
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
