package com.margelo.nitro.poselandmarks

import android.util.Log
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object PoseLandmarkerEngine {
  private val stateLock = Any()

  var latestLandmarks: DoubleArray = doubleArrayOf()
  var lastInferenceTimeMs: Double = -1.0

  var minVisibilityConfidence: Double = 0.9
  var enableVisibilityRecovery: Boolean = true
  var enableOneEuroFilter: Boolean = true
  var enableMotionPrediction: Boolean = false
  var oneEuroMinCutoff: Double = 1.0
  var oneEuroBeta: Double = 0.009

  private var oneEuroFilters: Array<OneEuroFilter>? = null
  private var previousSmoothedFrame: LandmarkFrame? = null
  private var latestSmoothedFrame: LandmarkFrame? = null
  private var latestGoodCoords: DoubleArray? = null
  private var latestGoodVisibility: DoubleArray? = null

  fun resetFilters() {
    synchronized(stateLock) {
      oneEuroFilters = null
      previousSmoothedFrame = null
      latestSmoothedFrame = null
      latestGoodCoords = null
      latestGoodVisibility = null
    }
  }

  fun feedRawResults(
    rawCoords: DoubleArray,
    rawVisibility: DoubleArray,
    timestampMs: Long,
    inferenceTimeMs: Double
  ) {
    synchronized(stateLock) {
      lastInferenceTimeMs = inferenceTimeMs

      if (rawVisibility.isEmpty()) return

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

      val filteredCoords = if (enableOneEuroFilter) {
        applyOneEuroFilters(coords, timestampMs)
      } else {
        coords
      }

      val frame = LandmarkFrame(
        timestampMs = timestampMs,
        coords = filteredCoords,
        visibility = visibility
      )

      previousSmoothedFrame = latestSmoothedFrame
      latestSmoothedFrame = frame

      latestGoodCoords = filteredCoords.copyOf()
      latestGoodVisibility = visibility.copyOf()

      latestLandmarks = flattenFrame(frame.coords, frame.visibility)
    }
  }

  fun getPredictedFrame(): DoubleArray {
    synchronized(stateLock) {
      val latest = latestSmoothedFrame ?: return latestLandmarks
      val previous = previousSmoothedFrame

      if (!enableMotionPrediction || previous == null) {
        return flattenFrame(latest.coords, latest.visibility)
      }

      val now = System.currentTimeMillis()
      val elapsedFromLatest = (now - latest.timestampMs).coerceAtLeast(0L).toDouble()

      // Only predict if we have enough elapsed time
      if (elapsedFromLatest < 8.0) {
        return flattenFrame(latest.coords, latest.visibility)
      }

      val baseDelta = (latest.timestampMs - previous.timestampMs).coerceAtLeast(1L).toDouble()
      val predictionDelta = min(elapsedFromLatest, 66.0)

      var totalVelocity = 0.0
      for (i in latest.coords.indices) {
        val velocity = (latest.coords[i] - previous.coords[i]) / baseDelta
        totalVelocity += abs(velocity)
      }
      val avgVelocity = totalVelocity / latest.coords.size

      if (avgVelocity < MOTION_THRESHOLD) {
        return flattenFrame(latest.coords, latest.visibility)
      }

      val predictedCoords = DoubleArray(latest.coords.size)
      for (i in latest.coords.indices) {
        val velocity = (latest.coords[i] - previous.coords[i]) / baseDelta
        predictedCoords[i] = latest.coords[i] + velocity * predictionDelta
      }

      return flattenFrame(predictedCoords, latest.visibility)
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

  private class OneEuroFilter {
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

  private const val MOTION_THRESHOLD = 0.0005
}
