package com.margelo.nitro.poselandmarks

import android.util.Log
import android.util.Size
import androidx.annotation.Keep
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.facebook.proguard.annotations.DoNotStrip
import android.os.Handler
import android.os.Looper

import com.margelo.nitro.NitroModules
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DoNotStrip
@Keep
open class HybridPoseLandmarks : HybridPoseLandmarksSpec(), PoseLandmarkerHelper.LandmarkerListener {
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private var latestLandmarks: DoubleArray = doubleArrayOf()
    private var lastInferenceTimeMs: Double = -1.0
    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null

    override fun initPoseLandmarker(): Boolean {
        Log.d("native-pose-landmarker", "called initPoseLandmarker, started initialization...")
        val context = NitroModules.applicationContext
        if (context == null) {
            Log.e("native-pose-landmarker", "context is null! NitroModules.applicationContext was not set.")
            return false
        }
        Log.d("native-pose-landmarker", "grabbed package context: $context")

        cameraExecutor = Executors.newSingleThreadExecutor()
        Log.d("native-pose-landmarker", "got a thread executor")

        poseLandmarkerHelper = PoseLandmarkerHelper(
            context = context,
            poseLandmarkerHelperListener = this
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
        Log.d("native-pose-landmarker", "pose landmarker initialization setup complete! returning true to JS")
        return true
    }

    private fun bindCameraUseCases() {
        Log.d("native-pose-landmarker", "bindCameraUseCases started")
        NitroModules.applicationContext ?: run {
            Log.e("native-pose-landmarker", "bindCameraUseCases: context is null")
            return
        }
        val cp = cameraProvider ?: run {
            Log.e("native-pose-landmarker", "bindCameraUseCases: cameraProvider is null")
            return
        }
        val executor = cameraExecutor ?: run {
            Log.e("native-pose-landmarker", "bindCameraUseCases: cameraExecutor is null")
            return
        }

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        Log.d("native-pose-landmarker", "using DEFAULT_FRONT_CAMERA")
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(256, 256),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()
        val imageAnalyzer = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(executor) { imageProxy ->
                    imageProxy.use { imageProxy ->
                        Log.v("native-pose-landmarker", "Analyzer received image: ${imageProxy.width}x${imageProxy.height}, rotation: ${imageProxy.imageInfo.rotationDegrees}")
                        poseLandmarkerHelper?.detectLiveStream(
                            imageProxy = imageProxy,
                            isFrontCamera = true
                        )
                    }

                }
            }

        try {
            cp.unbindAll()
            Log.d("native-pose-landmarker", "unbound all existing use cases")
            cp.bindToLifecycle(
                ProcessLifecycleOwner.get(),
                cameraSelector,
                imageAnalyzer
            )
            Log.d("native-pose-landmarker", "bound imageAnalyzer to lifecycle successfully")
        } catch (e: Exception) {
            Log.e("native-pose-landmarker", "Camera binding failed: ${e.message}", e)
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
        poseLandmarkerHelper?.clearPoseLandmarker()
        poseLandmarkerHelper = null
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

    override fun onError(error: String, errorCode: Int) {
        Log.e("native-pose-landmarker", "Error: $error (code: $errorCode)")
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        Log.v("native-pose-landmarker", "onResults received from helper. Results count: ${resultBundle.results.size}")
        lastInferenceTimeMs = resultBundle.inferenceTime.toDouble()
        val results = resultBundle.results
        if (results.isNotEmpty()) {
            val poseLandmarks = results[0].landmarks()
            if (poseLandmarks.isNotEmpty()) {
                val firstPose = poseLandmarks[0]
                Log.v("native-pose-landmarker", "detected ${firstPose.size} landmarks")
                val buffer = DoubleArray(firstPose.size * 4)
                for (i in firstPose.indices) {
                    val landmark = firstPose[i]
                    // x
                    buffer[i * 4] = landmark.y().toDouble()
                    // y
                    buffer[i * 4 + 1] = 1 - landmark.x().toDouble()
                    buffer[i * 4 + 2] = landmark.z().toDouble()
                    buffer[i * 4 + 3] = if (landmark.visibility().isPresent) landmark.visibility().get().toDouble() else 1.0
                }
                latestLandmarks = buffer
                Log.v("native-pose-landmarker", "updated latestLandmarks buffer with ${buffer.size} values")
            } else {
                Log.v("native-pose-landmarker", "no pose landmarks in first result")
                latestLandmarks = DoubleArray(0)
            }
        } else {
            Log.v("native-pose-landmarker", "onResults had empty results list")
            latestLandmarks = DoubleArray(0)
        }
    }
}
