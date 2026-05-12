package com.margelo.nitro.poselandmarks

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.Keep
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.facebook.proguard.annotations.DoNotStrip
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.margelo.nitro.NitroModules
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@DoNotStrip
@Keep
open class HybridPoseLandmarks : HybridPoseLandmarksSpec() {
    private val stateLock = Any()

    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private var latestLandmarks: DoubleArray = doubleArrayOf()
    private var lastInferenceTimeMs: Double = -1.0
    private var cameraExecutor: ExecutorService? = null
    private var outputExecutor: ScheduledExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var minVisibilityConfidence: Double = 0.9
    private var inferenceSampleRateHz: Double = 30.0
    private var selectedModel: Int = PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_LITE
    private var selectedDelegate: Int = PoseLandmarkerHelper.DELEGATE_CPU
    private var enableVisibilityRecovery: Boolean = true
    private var enableOneEuroFilter: Boolean = true
    private var enableMotionPrediction: Boolean = false
    private var oneEuroMinCutoff: Double = 1.0
    private var oneEuroBeta: Double = 0.009
    private var lastInferenceRequestTimeMs: Long = 0L

    private var oneEuroFilters: Array<OneEuroFilter>? = null
    private var previousSmoothedFrame: LandmarkFrame? = null
    private var latestSmoothedFrame: LandmarkFrame? = null
    private var latestGoodCoords: DoubleArray? = null
    private var latestGoodVisibility: DoubleArray? = null
    private val inferenceTimestamps = java.util.concurrent.ConcurrentHashMap<Long, Long>()

override fun initPoseLandmarker(
        minVisibilityConfidence: Double?,
        inferenceSampleRateHz: Double?,
        rigidBodyWindowFrames: Double?,
        modelSelection: Double?,
        delegateSelection: Double?,
        enableVisibilityRecovery: Boolean?,
        enableRigidBodyConstraint: Boolean?,
        enableOneEuroFilter: Boolean?,
        enableMotionPrediction: Boolean?,
        oneEuroMinCutoff: Double?,
        oneEuroBeta: Double?
    ): Boolean {
        Log.d("native-pose-landmarker", "called initPoseLandmarker, started initialization...")
        applyProcessingConfig(
            minVisibilityConfidence = minVisibilityConfidence,
            inferenceSampleRateHz = inferenceSampleRateHz,
            rigidBodyWindowFrames = rigidBodyWindowFrames,
            modelSelection = modelSelection,
            delegateSelection = delegateSelection,
            enableVisibilityRecovery = enableVisibilityRecovery,
            enableRigidBodyConstraint = enableRigidBodyConstraint,
            enableOneEuroFilter = enableOneEuroFilter,
            enableMotionPrediction = enableMotionPrediction,
            oneEuroMinCutoff = oneEuroMinCutoff,
            oneEuroBeta = oneEuroBeta
        )

        val context = NitroModules.applicationContext
        if (context == null) {
            Log.e("native-pose-landmarker", "context is null! NitroModules.applicationContext was not set.")
            return false
        }
        Log.d("native-pose-landmarker", "grabbed package context: $context")

        cameraExecutor = Executors.newSingleThreadExecutor()
        outputExecutor = Executors.newSingleThreadScheduledExecutor()
        Log.d("native-pose-landmarker", "got a thread executor")

        poseLandmarkerHelper = PoseLandmarkerHelper(
            currentModel = selectedModel,
            currentDelegate = selectedDelegate,
            context = context,
            onResult = { result ->
                processLivestreamResult(result)
            },
        )
        Log.d("native-pose-landmarker", "got a PoseLandmarkerHelper instance")
        if (poseLandmarkerHelper?.isInitialized() != true) {
            Log.e("native-pose-landmarker", "pose landmarker helper failed to initialize")
            poseLandmarkerHelper = null
            cameraExecutor?.shutdown()
            cameraExecutor = null
            return false
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                Log.d("native-pose-landmarker", "cameraProvider retrieved, binding use cases...")
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e("native-pose-landmarker", "failed to get cameraProvider: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))

        startPredictionLoop()
        Log.d("native-pose-landmarker", "pose landmarker initialization setup complete! returning true to JS")
        return true
    }

private fun applyProcessingConfig(
        minVisibilityConfidence: Double?,
        inferenceSampleRateHz: Double?,
        rigidBodyWindowFrames: Double?,
        modelSelection: Double?,
        delegateSelection: Double?,
        enableVisibilityRecovery: Boolean?,
        enableRigidBodyConstraint: Boolean?,
        enableOneEuroFilter: Boolean?,
        enableMotionPrediction: Boolean?,
        oneEuroMinCutoff: Double?,
        oneEuroBeta: Double?
    ) {
        synchronized(stateLock) {
            this.minVisibilityConfidence = (minVisibilityConfidence ?: this.minVisibilityConfidence).coerceIn(0.0, 1.0)
            this.inferenceSampleRateHz = (inferenceSampleRateHz ?: this.inferenceSampleRateHz).coerceIn(1.0, TARGET_OUTPUT_HZ)
            this.enableVisibilityRecovery = enableVisibilityRecovery ?: this.enableVisibilityRecovery
            this.enableOneEuroFilter = enableOneEuroFilter ?: this.enableOneEuroFilter
            this.enableMotionPrediction = enableMotionPrediction ?: this.enableMotionPrediction
            this.oneEuroMinCutoff = oneEuroMinCutoff?.coerceIn(0.01, 5.0) ?: this.oneEuroMinCutoff
            this.oneEuroBeta = oneEuroBeta?.coerceIn(0.0, 1.0) ?: this.oneEuroBeta

            val model = (modelSelection ?: selectedModel.toDouble()).toInt()
            selectedModel = when (model) {
                PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_FULL,
                PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_LITE,
                PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_HEAVY -> model
                else -> PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_LITE
            }

            val delegate = (delegateSelection ?: selectedDelegate.toDouble()).toInt()
            selectedDelegate = when (delegate) {
                PoseLandmarkerHelper.DELEGATE_CPU,
                PoseLandmarkerHelper.DELEGATE_GPU -> delegate
                else -> PoseLandmarkerHelper.DELEGATE_CPU
            }

            oneEuroFilters = null
            previousSmoothedFrame = null
            latestSmoothedFrame = null
            latestGoodCoords = null
            latestGoodVisibility = null
            lastInferenceRequestTimeMs = 0L
        }
    }

    private fun bindCameraUseCases() {
        Log.d("native-pose-landmarker", "bindCameraUseCases started")
        val cp = cameraProvider ?: run {
            Log.e("native-pose-landmarker", "bindCameraUseCases: cameraProvider is null")
            return
        }
        val executor = cameraExecutor ?: run {
            Log.e("native-pose-landmarker", "bindCameraUseCases: cameraExecutor is null")
            return
        }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(executor) { imageProxy ->
                    if (!shouldRunInference()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    val bitmap = imageProxyToBitmap(imageProxy)
                    val rawTimestampNs = imageProxy.imageInfo.timestamp
                    val timestampMs = if (rawTimestampNs != null) rawTimestampNs / 1_000_000 else System.currentTimeMillis()
                    imageProxy.close()
                    if (bitmap == null) return@setAnalyzer

                    // CameraX rotationDegrees is CLOCKWISE, same as MediaPipe's ImageProcessingOptions
                    val mpRotation = rotationDegrees
                    inferenceTimestamps[timestampMs] = SystemClock.uptimeMillis()
                    poseLandmarkerHelper?.detectAsync(bitmap, timestampMs, mpRotation)
                    bitmap.recycle()
                }
            }

        try {
            cp.unbindAll()
            Log.d("native-pose-landmarker", "unbound all existing use cases")
            cp.bindToLifecycle(
                ProcessLifecycleOwner.get(),
                CameraSelector.DEFAULT_FRONT_CAMERA,
                imageAnalyzer
            )
            Log.d("native-pose-landmarker", "bound imageAnalyzer to lifecycle successfully")
        } catch (e: Exception) {
            Log.e("native-pose-landmarker", "Camera binding failed: ${e.message}", e)
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        try {
            val width = imageProxy.width
            val height = imageProxy.height
            if (width <= 0 || height <= 0) return null
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val plane = imageProxy.planes[0]
            val buffer = plane.buffer
            buffer.rewind()
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            if (rowStride == width * pixelStride) {
                bitmap.copyPixelsFromBuffer(buffer)
            } else {
                val pixels = IntArray(width * height)
                for (y in 0 until height) {
                    buffer.position(y * rowStride)
                    for (x in 0 until width) {
                        val r = buffer.get().toInt() and 0xFF
                        val g = buffer.get().toInt() and 0xFF
                        val b = buffer.get().toInt() and 0xFF
                        val a = buffer.get().toInt() and 0xFF
                        pixels[y * width + x] = if (pixelStride == 4)
                            (a shl 24) or (r shl 16) or (g shl 8) or b
                        else
                            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            }
            return bitmap
        } catch (e: Exception) {
            Log.e("native-pose-landmarker", "imageProxyToBitmap failed: ${e.message}")
            return null
        }
    }

    override fun closePoseLandmarker(): Boolean {
        Log.d("native-pose-landmarker", "closing pose landmarker...")
        val cp = cameraProvider
        if (cp != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                cp.unbindAll()
            } else {
                val latch = CountDownLatch(1)
                Handler(Looper.getMainLooper()).post {
                    try {
                        cp.unbindAll()
                    } finally {
                        latch.countDown()
                    }
                }
                latch.await(2, TimeUnit.SECONDS)
            }
        }
        cameraProvider = null
        cameraExecutor?.shutdown()
        cameraExecutor = null
        outputExecutor?.shutdown()
        outputExecutor = null
        poseLandmarkerHelper?.clearPoseLandmarker()
        poseLandmarkerHelper = null

        synchronized(stateLock) {
            latestLandmarks = doubleArrayOf()
            lastInferenceTimeMs = -1.0
            oneEuroFilters = null
            previousSmoothedFrame = null
            latestSmoothedFrame = null
            latestGoodCoords = null
            latestGoodVisibility = null
            lastInferenceRequestTimeMs = 0L
        }

        Log.d("native-pose-landmarker", "pose landmarker closed")
        return true
    }

    override fun getLandmarksBuffer(): DoubleArray {
        Log.v("native-pose-landmarker", "getLandmarksBuffer called. Buffer length=${latestLandmarks.size}")
        return latestLandmarks
    }

    override fun getLastInferenceTimeMs(): Double {
        Log.v("native-pose-landmarker", "getLastInferenceTimeMs called. lastInferenceTimeMs=$lastInferenceTimeMs")
        return lastInferenceTimeMs
    }

    private fun shouldRunInference(): Boolean {
        synchronized(stateLock) {
            if (inferenceSampleRateHz >= TARGET_OUTPUT_HZ - 0.1) {
                lastInferenceRequestTimeMs = System.currentTimeMillis()
                return true
            }
            val now = System.currentTimeMillis()
            val minIntervalMs = (1000.0 / inferenceSampleRateHz).toLong().coerceAtLeast(1L)
            if (now - lastInferenceRequestTimeMs < minIntervalMs) {
                return false
            }
            lastInferenceRequestTimeMs = now
            return true
        }
    }

    private fun processAndStoreFrame(rawCoords: DoubleArray, rawVisibility: DoubleArray, timestampMs: Long) {
        synchronized(stateLock) {
            if (rawVisibility.isEmpty()) {
                return
            }

            ensureFilterCapacity(rawCoords.size)

            val coords = rawCoords.copyOf()
            val visibility = rawVisibility.copyOf()

            val lastGood = latestGoodCoords
            if (enableVisibilityRecovery) {
                for (index in visibility.indices) {
                    if (visibility[index] < minVisibilityConfidence && lastGood != null && lastGood.size == coords.size) {
                        coords[index * 3] = lastGood[index * 3]
                        coords[index * 3 + 1] = lastGood[index * 3 + 1]
                        coords[index * 3 + 2] = lastGood[index * 3 + 2]
                    }
                }
            }

            val badSignal = false  // rigid body constraint removed - caused jitter without benefit

            val filteredCoords = if (badSignal && lastGood != null && lastGood.size == coords.size) {
                lastGood.copyOf()
            } else {
                if (enableOneEuroFilter) {
                    applyOneEuroFilters(coords, timestampMs)
                } else {
                    coords
                }
            }

            val frame = LandmarkFrame(
                timestampMs = timestampMs,
                coords = filteredCoords,
                visibility = visibility
            )

            previousSmoothedFrame = latestSmoothedFrame
            latestSmoothedFrame = frame

            if (!badSignal) {
                latestGoodCoords = filteredCoords.copyOf()
                latestGoodVisibility = visibility.copyOf()
            }

            latestLandmarks = flattenFrame(frame.coords, frame.visibility)
            Log.v("native-pose-landmarker", "updated latestLandmarks buffer with ${latestLandmarks.size} values")
        }
    }

    private fun processLivestreamResult(result: PoseLandmarkerResult) {
        try {
            val landmarks = result.landmarks()
            if (landmarks.isNotEmpty() && landmarks[0].isNotEmpty()) {
                val firstPose = landmarks[0]
                val rawCoords = DoubleArray(firstPose.size * 3)
                val visibility = DoubleArray(firstPose.size)
                for (i in firstPose.indices) {
                    val lm = firstPose[i]
                    rawCoords[i * 3] = (1 - lm.y()).toDouble()
                    rawCoords[i * 3 + 1] = (1 - lm.x()).toDouble()
                    rawCoords[i * 3 + 2] = lm.z().toDouble()
                    visibility[i] = if (lm.visibility().isPresent) lm.visibility().get().toDouble() else 1.0
                }
                val submissionMs = inferenceTimestamps.remove(result.timestampMs()) ?: SystemClock.uptimeMillis()
                val inferenceTimeMs = (SystemClock.uptimeMillis() - submissionMs).coerceAtLeast(0L).toDouble()
                lastInferenceTimeMs = inferenceTimeMs
                processAndStoreFrame(rawCoords, visibility, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Log.e("native-pose-landmarker", "processLivestreamResult: error: ${e.message}", e)
        }
    }

    private fun startPredictionLoop() {
        outputExecutor?.scheduleAtFixedRate(
            { publishPredictedFrame() },
            0L,
            TARGET_OUTPUT_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun publishPredictedFrame() {
        synchronized(stateLock) {
            val latest = latestSmoothedFrame ?: return
            val previous = previousSmoothedFrame

            if (!enableMotionPrediction || inferenceSampleRateHz >= TARGET_OUTPUT_HZ - 0.1 || previous == null) {
                latestLandmarks = flattenFrame(latest.coords, latest.visibility)
                return
            }

            val now = System.currentTimeMillis()
            val elapsedFromLatest = (now - latest.timestampMs).coerceAtLeast(0L).toDouble()
            val expectedInferenceIntervalMs = 1000.0 / inferenceSampleRateHz

            if (elapsedFromLatest < expectedInferenceIntervalMs * 0.75) {
                latestLandmarks = flattenFrame(latest.coords, latest.visibility)
                return
            }

            val baseDelta = (latest.timestampMs - previous.timestampMs).coerceAtLeast(1L).toDouble()
            val predictionDelta = min(elapsedFromLatest, expectedInferenceIntervalMs * 2.0)

            var totalVelocity = 0.0
            for (i in latest.coords.indices) {
                val velocity = (latest.coords[i] - previous.coords[i]) / baseDelta
                totalVelocity += abs(velocity)
            }
            val avgVelocity = totalVelocity / latest.coords.size

            if (avgVelocity < MOTION_THRESHOLD) {
                latestLandmarks = flattenFrame(latest.coords, latest.visibility)
                return
            }

            val predictedCoords = DoubleArray(latest.coords.size)
            for (i in latest.coords.indices) {
                val velocity = (latest.coords[i] - previous.coords[i]) / baseDelta
                predictedCoords[i] = latest.coords[i] + velocity * predictionDelta
            }

            latestLandmarks = flattenFrame(predictedCoords, latest.visibility)
        }
    }

    private fun ensureFilterCapacity(coordCount: Int) {
        val filters = oneEuroFilters
        if (filters == null || filters.size != coordCount) {
            oneEuroFilters = Array(coordCount) { OneEuroFilter() }
        }
        if (filters != null) {
            for (f in filters) {
                f.configure(oneEuroMinCutoff, oneEuroBeta)
            }
        } else {
            for (f in oneEuroFilters!!) {
                f.configure(oneEuroMinCutoff, oneEuroBeta)
            }
        }
    }

    private fun applyOneEuroFilters(coords: DoubleArray, timestampMs: Long): DoubleArray {
        val filters = oneEuroFilters ?: return coords
        val filtered = DoubleArray(coords.size)
        for (i in coords.indices) {
            filtered[i] = filters[i].filter(coords[i], timestampMs.toDouble() / 1000.0)
        }
        return filtered
    }

    private fun flattenFrame(coords: DoubleArray, visibility: DoubleArray): DoubleArray {
        val landmarkCount = visibility.size
        val buffer = DoubleArray(landmarkCount * 4)
        for (i in 0 until landmarkCount) {
            buffer[i * 4] = coords[i * 3]
            buffer[i * 4 + 1] = coords[i * 3 + 1]
            buffer[i * 4 + 2] = coords[i * 3 + 2]
            buffer[i * 4 + 3] = visibility[i]
        }
        return buffer
    }

    private data class LandmarkFrame(
        val timestampMs: Long,
        val coords: DoubleArray,
        val visibility: DoubleArray,
    )

    private class OneEuroFilter(
    ) {
        private var minCutoff: Double = 0.4
        private var beta: Double = 0.007
        private val dCutoff: Double = 1.0
        private var initialized = false
        private var previousTimestampSec = 0.0
        private var previousValue = 0.0
        private var previousDerivative = 0.0

        fun configure(minCutoff: Double, beta: Double) {
            this.minCutoff = minCutoff
            this.beta = beta
        }

        fun filter(value: Double, timestampSec: Double): Double {
            if (!initialized) {
                initialized = true
                previousTimestampSec = timestampSec
                previousValue = value
                previousDerivative = 0.0
                return value
            }

            val dt = max(1e-3, timestampSec - previousTimestampSec)
            val frequency = 1.0 / dt

            val derivative = (value - previousValue) * frequency
            val alphaD = alpha(frequency, dCutoff)
            val smoothedDerivative = lowPass(alphaD, derivative, previousDerivative)

            val cutoff = minCutoff + beta * abs(smoothedDerivative)
            val alpha = alpha(frequency, cutoff)
            val smoothedValue = lowPass(alpha, value, previousValue)

            previousTimestampSec = timestampSec
            previousDerivative = smoothedDerivative
            previousValue = smoothedValue
            return smoothedValue
        }

        private fun alpha(frequency: Double, cutoff: Double): Double {
            val te = 1.0 / max(frequency, 1e-6)
            val tau = 1.0 / (2.0 * PI * cutoff)
            return 1.0 / (1.0 + tau / te)
        }

        private fun lowPass(alpha: Double, value: Double, previous: Double): Double {
            return alpha * value + (1.0 - alpha) * previous
        }
    }

    companion object {
        private const val TARGET_OUTPUT_HZ = 30.0
        private const val TARGET_OUTPUT_INTERVAL_MS = 33L
        private const val MOTION_THRESHOLD = 0.0005  // skip prediction if avg velocity below this
    }
}
