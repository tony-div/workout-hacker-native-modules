package com.margelo.nitro.poselandmarks

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.facebook.react.uimanager.ThemedReactContext
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class HybridPoseLandmarksView(private val reactContext: ThemedReactContext) : HybridPoseLandmarksViewSpec() {
  private val container: FrameLayout
  private val overlayView: SkeletonOverlayView
  override val view: View get() = container

  private var cameraProvider: ProcessCameraProvider? = null
  private var cameraExecutor = Executors.newSingleThreadExecutor()
  private var poseLandmarkerHelper: PoseLandmarkerHelper? = null

  override var isActive: Boolean = false
    set(value) {
      if (field == value) return
      field = value
      if (value) startCamera() else stopCamera()
    }

  override var enableSkeleton: Boolean = true
    set(value) {
      field = value
      overlayView.visibility = if (value) View.VISIBLE else View.GONE
    }

  override var skeletonColor: String = "#00FF00"
    set(value) {
      field = value
      runCatching { overlayView.skeletonColor = android.graphics.Color.parseColor(value) }
    }

  override var skeletonBoneThickness: Double = 6.0
    set(value) {
      field = value
      overlayView.connectionStrokeWidth = value.toFloat().coerceIn(0.5f, 30f)
    }

  override var landmarkColor: String = "#FF0000"
    set(value) {
      field = value
      runCatching { overlayView.landmarkColor = android.graphics.Color.parseColor(value) }
    }

  override var minVisibilityConfidence: Double = 0.5
    set(value) {
      field = value.coerceIn(0.0, 1.0)
      overlayView.minVisibility = field
      PoseLandmarkerEngine.minVisibilityConfidence = field
    }

  override var modelSelection: Double = PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_LITE.toDouble()
  override var inferenceSampleRateHz: Double = 30.0
  override var enableVisibilityRecovery: Boolean = true
    set(value) {
      field = value
      PoseLandmarkerEngine.enableVisibilityRecovery = value
    }
  override var enableOneEuroFilter: Boolean = true
    set(value) {
      field = value
      PoseLandmarkerEngine.enableOneEuroFilter = value
    }
  override var enableMotionPrediction: Boolean = false
    set(value) {
      field = value
      PoseLandmarkerEngine.enableMotionPrediction = value
    }
  override var oneEuroMinCutoff: Double = 1.0
    set(value) {
      field = value
      PoseLandmarkerEngine.oneEuroMinCutoff = value.coerceIn(0.01, 5.0)
    }
  override var oneEuroBeta: Double = 0.009
    set(value) {
      field = value
      PoseLandmarkerEngine.oneEuroBeta = value.coerceIn(0.0, 1.0)
    }

  override var width: Double = 0.0
  override var height: Double = 0.0

  init {
    container = FrameLayout(reactContext)
    container.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
      Log.d(TAG, "container onLayout: ${right - left}x${bottom - top}, overlay=${overlayView.width}x${overlayView.height}")
    }
    Log.d(TAG, "View created")

    overlayView = SkeletonOverlayView(reactContext)
    overlayView.layoutParams = FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    overlayView.visibility = View.VISIBLE
    container.addView(overlayView)
  }

  override fun beforeUpdate() {}

  override fun afterUpdate() {
    val density = reactContext.resources.displayMetrics.density
    Log.d(TAG, "afterUpdate: width=$width height=$height density=$density")
    Log.d(TAG, "afterUpdate: container layoutParams=(${container.layoutParams?.width}x${container.layoutParams?.height}) actual=(${container.width}x${container.height})")
    if (width > 0 && height > 0) {
      val wPx = (width * density).roundToInt()
      val hPx = (height * density).roundToInt()
      val lp = container.layoutParams
      if (lp?.width != wPx || lp?.height != hPx) {
        container.layoutParams = FrameLayout.LayoutParams(wPx, hPx)
      }
      overlayView.overlayWidth = wPx
      overlayView.overlayHeight = hPx
      Log.d(TAG, "afterUpdate: set overlay size to ${wPx}x${hPx}")
    }
  }

  override fun onDropView() {
    stopCamera()
    cameraExecutor.shutdown()
  }

  private fun startCamera() {
    val context = reactContext
    val lifecycleOwner: LifecycleOwner = context.currentActivity as? LifecycleOwner
      ?: run {
        Log.e(TAG, "startCamera: no LifecycleOwner available")
        return
      }

    val model = modelSelection.toInt().let { m ->
      when (m) {
        PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_FULL,
        PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_LITE,
        PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_HEAVY -> m
        else -> PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_LITE
      }
    }

    Log.d(TAG, "startCamera: model=$model containerSize=${container.width}x${container.height}")

    PoseLandmarkerEngine.minVisibilityConfidence = minVisibilityConfidence
    PoseLandmarkerEngine.enableVisibilityRecovery = enableVisibilityRecovery
    PoseLandmarkerEngine.enableOneEuroFilter = enableOneEuroFilter
    PoseLandmarkerEngine.enableMotionPrediction = enableMotionPrediction
    PoseLandmarkerEngine.oneEuroMinCutoff = oneEuroMinCutoff
    PoseLandmarkerEngine.oneEuroBeta = oneEuroBeta
    PoseLandmarkerEngine.resetFilters()

    poseLandmarkerHelper = PoseLandmarkerHelper(
      currentModel = model,
      context = context,
    )
    if (poseLandmarkerHelper?.isInitialized() != true) {
      Log.e(TAG, "startCamera: PoseLandmarkerHelper failed to initialize")
      poseLandmarkerHelper = null
      return
    }

    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
      try {
        val provider = cameraProviderFuture.get()
        cameraProvider = provider
        Log.d(TAG, "startCamera: cameraProvider obtained, binding use cases")
        bindUseCases(provider, lifecycleOwner)
      } catch (e: Exception) {
        Log.e(TAG, "startCamera: failed to get cameraProvider: ${e.message}", e)
      }
    }, ContextCompat.getMainExecutor(context))
  }

  private fun bindUseCases(provider: ProcessCameraProvider, lifecycleOwner: LifecycleOwner) {
    val imageAnalyzer = ImageAnalysis.Builder()
      .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
      .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
      .build()
      .also { analysis ->
        Log.d(TAG, "bindUseCases: setting analyzer on ImageAnalysis=$analysis")
        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
          processFrame(imageProxy)
        }
        Log.d(TAG, "bindUseCases: analyzer set successfully")
      }

    cameraExecutor.submit {
      Log.d(TAG, "bindUseCases: cameraExecutor test task - executor is alive")
    }

    try {
      provider.unbindAll()
      val usage = provider.bindToLifecycle(
        lifecycleOwner,
        CameraSelector.DEFAULT_FRONT_CAMERA,
        imageAnalyzer
      )
      Log.d(TAG, "bindUseCases: bound successfully, cameraInfo=${usage.cameraInfo}")
    } catch (e: Exception) {
      Log.e(TAG, "bindUseCases: failed: ${e.message}", e)
    }
  }

  private var lastFrameTime = 0L
  private var frameSkipCount = 0

  private fun processFrame(imageProxy: ImageProxy) {
    val now = System.currentTimeMillis()
    if (now - lastFrameTime < 33) {
      frameSkipCount++
      imageProxy.close()
      return
    }
    if (frameSkipCount > 0) {
      Log.d(TAG, "processFrame: skipped $frameSkipCount frames")
      frameSkipCount = 0
    }
    lastFrameTime = now

    val helper = poseLandmarkerHelper
    if (helper == null || !isActive) {
      imageProxy.close()
      return
    }

    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
    val bitmap = imageProxyToBitmap(imageProxy)
    imageProxy.close()
    if (bitmap == null) return

    Log.d(TAG, "processFrame: ${bitmap.width}x${bitmap.height} rotation=$rotationDegrees")

    // Resize for faster inference (320x240 is ~1/4 the pixels of 640x480)
    val inferenceBitmap = if (bitmap.width > 320) {
      val scale = 320f / maxOf(bitmap.width, bitmap.height)
      val iw = (bitmap.width * scale).toInt().coerceAtLeast(160)
      val ih = (bitmap.height * scale).toInt().coerceAtLeast(120)
      Bitmap.createScaledBitmap(bitmap, iw, ih, true)
    } else bitmap

    val inferenceStart = SystemClock.uptimeMillis()
    val result = helper.detectSync(inferenceBitmap, 0)
    val inferenceTimeMs = SystemClock.uptimeMillis() - inferenceStart

    if (inferenceBitmap !== bitmap) {
      inferenceBitmap.recycle()
    }

    if (result != null) {
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

        PoseLandmarkerEngine.feedRawResults(
          rawCoords = rawCoords,
          rawVisibility = visibility,
          timestampMs = System.currentTimeMillis(),
          inferenceTimeMs = inferenceTimeMs.toDouble()
        )
      }
    }

    // Feed camera frame (rotated for display) + landmarks to overlay
    val engineLandmarks = PoseLandmarkerEngine.latestLandmarks
    reactContext.runOnUiQueueThread {
      overlayView.setCameraFrame(bitmap, rotationDegrees)
      if (engineLandmarks.isNotEmpty()) {
        overlayView.setLandmarks(engineLandmarks)
      }
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
            if (pixelStride == 4) {
              pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            } else {
              pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
          }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
      }
      return bitmap
    } catch (e: Exception) {
      Log.e(TAG, "imageProxyToBitmap: failed: ${e.message}")
      return null
    }
  }

  private fun stopCamera() {
    try {
      cameraProvider?.unbindAll()
    } catch (_: Exception) {}
    cameraProvider = null
    poseLandmarkerHelper?.clearPoseLandmarker()
    poseLandmarkerHelper = null
    overlayView.clearLandmarks()
    overlayView.clearCameraFrame()
  }

  override fun getLandmarksBuffer(): DoubleArray = PoseLandmarkerEngine.latestLandmarks
  override fun getLastInferenceTimeMs(): Double = PoseLandmarkerEngine.lastInferenceTimeMs

  companion object {
    private const val TAG = "HybridPoseLandmarksView"
  }
}
