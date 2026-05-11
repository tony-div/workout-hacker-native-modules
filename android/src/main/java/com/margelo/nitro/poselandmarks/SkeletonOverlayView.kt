package com.margelo.nitro.poselandmarks

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.max
import kotlin.math.min

class SkeletonOverlayView(context: Context) : View(context) {
  private var landmarks: DoubleArray = doubleArrayOf()
  private var cameraFrame: Bitmap? = null
  private var cameraFrameMatrix = Matrix()

  var skeletonColor: Int = Color.parseColor("#00FF00")
    set(value) {
      field = value
      landmarkPaint.color = value
      connectionPaint.color = value
    }
  var landmarkColor: Int = Color.parseColor("#FF0000")
    set(value) {
      field = value
      landmarkDotPaint.color = value
    }
  var connectionStrokeWidth: Float = 6f
    set(value) {
      field = value
      connectionPaint.strokeWidth = value
      landmarkPaint.strokeWidth = value
    }
  var minVisibility: Double = 0.5
  var overlayWidth: Int = 0
  var overlayHeight: Int = 0

  private val landmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = skeletonColor
    strokeWidth = connectionStrokeWidth
    style = Paint.Style.STROKE
  }
  private val connectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = skeletonColor
    strokeWidth = connectionStrokeWidth
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }
  private val landmarkDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = landmarkColor
    style = Paint.Style.FILL
  }
  private val path = Path()
  private val landmarkRadius = 6f

  private companion object {
    val POSE_CONNECTIONS: Array<Pair<Int, Int>> = arrayOf(
      0 to 1, 1 to 2, 2 to 3, 3 to 7,
      0 to 4, 4 to 5, 5 to 6, 6 to 8,
      9 to 10,
      11 to 12, 11 to 23, 11 to 13,
      12 to 24, 12 to 14,
      13 to 15,
      14 to 16,
      15 to 17, 15 to 19, 15 to 21,
      16 to 18, 16 to 20, 16 to 22,
      17 to 19,
      18 to 20,
      23 to 24, 23 to 25,
      24 to 26,
      25 to 27,
      26 to 28,
      27 to 29, 27 to 31,
      28 to 30, 28 to 32,
      29 to 31,
      30 to 32,
    )
  }

  fun setCameraFrame(bitmap: Bitmap, rotationDegrees: Int = 0) {
    val old = cameraFrame
    val rotated: Bitmap
    val displayW: Int
    val displayH: Int
    if (rotationDegrees != 0) {
      // rotationDegrees from CameraX is CLOCKWISE → postRotate(positive) is CW
      val m = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
      rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
      displayW = rotated.width
      displayH = rotated.height
    } else {
      rotated = bitmap.copy(Bitmap.Config.ARGB_8888, false)
      displayW = bitmap.width
      displayH = bitmap.height
    }
    cameraFrame = rotated
    old?.recycle()
    val vw = overlayWidth.coerceAtLeast(1)
    val vh = overlayHeight.coerceAtLeast(1)
    if (vw > 0 && vh > 0) {
      cameraFrameMatrix.setScale(-1f, 1f)
      cameraFrameMatrix.postScale(vw.toFloat() / displayW, vh.toFloat() / displayH)
      cameraFrameMatrix.postTranslate(vw.toFloat(), 0f)
    }
    postInvalidate()
  }

  fun clearCameraFrame() {
    cameraFrame = null
    postInvalidate()
  }

  fun setLandmarks(buffer: DoubleArray) {
    landmarks = buffer
    postInvalidate()
  }

  fun clearLandmarks() {
    landmarks = doubleArrayOf()
    postInvalidate()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    val cf = cameraFrame
    if (cf != null) {
      canvas.drawBitmap(cf, cameraFrameMatrix, null)
    }

    if (landmarks.isEmpty()) return

    val vw = overlayWidth.coerceAtLeast(1)
    val vh = overlayHeight.coerceAtLeast(1)
    val count = landmarks.size / 4

    if (count < 33) return

    // Draw connections
    for ((i, j) in POSE_CONNECTIONS) {
      if (i >= count || j >= count) continue
      val vi = landmarks[i * 4 + 3]
      val vj = landmarks[j * 4 + 3]
      if (vi < minVisibility || vj < minVisibility) continue

      val xi = landmarks[i * 4] * vw
      val yi = landmarks[i * 4 + 1] * vh
      val xj = landmarks[j * 4] * vw
      val yj = landmarks[j * 4 + 1] * vh

      path.rewind()
      path.moveTo(xi.toFloat(), yi.toFloat())
      path.lineTo(xj.toFloat(), yj.toFloat())
      canvas.drawPath(path, connectionPaint)
    }

    // Draw landmark dots
    for (i in 0 until count) {
      val v = landmarks[i * 4 + 3]
      if (v < minVisibility) continue

      val x = landmarks[i * 4] * vw
      val y = landmarks[i * 4 + 1] * vh

      val dotRadius = if (v > 0.8) landmarkRadius else landmarkRadius * 0.6f
      canvas.drawCircle(x.toFloat(), y.toFloat(), dotRadius, landmarkDotPaint)

      canvas.drawCircle(x.toFloat(), y.toFloat(), dotRadius + 1.5f, landmarkPaint)
    }
  }
}
