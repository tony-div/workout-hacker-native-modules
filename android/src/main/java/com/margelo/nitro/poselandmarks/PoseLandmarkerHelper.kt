/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.margelo.nitro.poselandmarks

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseLandmarkerHelper(
    private var minPoseDetectionConfidence: Float = DEFAULT_POSE_DETECTION_CONFIDENCE,
    private var minPoseTrackingConfidence: Float = DEFAULT_POSE_TRACKING_CONFIDENCE,
    private var minPosePresenceConfidence: Float = DEFAULT_POSE_PRESENCE_CONFIDENCE,
    private var currentModel: Int = MODEL_POSE_LANDMARKER_LITE,
    private var currentDelegate: Int = DELEGATE_CPU,
    val context: Context,
) {
    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    fun isInitialized(): Boolean {
        return poseLandmarker != null
    }

    private fun setupPoseLandmarker() {
        val baseOptionBuilder = BaseOptions.builder()

        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionBuilder.setDelegate(Delegate.GPU)
        }

        val modelName =
            when (currentModel) {
                MODEL_POSE_LANDMARKER_FULL -> "pose_landmarker_full.task"
                MODEL_POSE_LANDMARKER_LITE -> "pose_landmarker_lite.task"
                MODEL_POSE_LANDMARKER_HEAVY -> "pose_landmarker_heavy.task"
                else -> "pose_landmarker_lite.task"
            }

        val modelAssetExists = runCatching {
            context.assets.open(modelName).use { }
            true
        }.getOrElse { false }
        if (!modelAssetExists) {
            val rootAssets = runCatching { context.assets.list("")?.joinToString(", ") ?: "<empty>" }
                .getOrElse { "<unavailable>" }
            Log.e(TAG, "setupPoseLandmarker: model asset '$modelName' missing. Root: $rootAssets")
            poseLandmarker = null
            return
        }
        Log.d(TAG, "setupPoseLandmarker: found '$modelName'")

        baseOptionBuilder.setModelAssetPath(modelName)

        try {
            val baseOptions = baseOptionBuilder.build()
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                .setMinTrackingConfidence(minPoseTrackingConfidence)
                .setMinPosePresenceConfidence(minPosePresenceConfidence)
                .setRunningMode(RunningMode.IMAGE)
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            Log.d(TAG, "setupPoseLandmarker: created successfully (IMAGE mode)")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "setupPoseLandmarker: failed: ${e.message}", e)
        } catch (e: RuntimeException) {
            Log.e(TAG, "setupPoseLandmarker: failed (GPU?): ${e.message}", e)
        }
    }

    fun detectSync(bitmap: Bitmap, rotationDegrees: Int): PoseLandmarkerResult? {
        val landmarker = poseLandmarker
        if (landmarker == null) {
            Log.e(TAG, "detectSync: poseLandmarker is null")
            return null
        }

        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val options = ImageProcessingOptions.builder()
                .setRotationDegrees(rotationDegrees)
                .build()

            val result = landmarker.detect(mpImage, options)
            Log.d(TAG, "detectSync: result landmarks=${result?.landmarks()?.size} poses=${result?.landmarks()?.get(0)?.size}")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "detectSync: error: ${e.message}", e)
            return null
        }
    }

    companion object {
        const val TAG = "PoseLandmarkerHelper"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_POSE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_NUM_POSES = 1
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        const val MODEL_POSE_LANDMARKER_FULL = 0
        const val MODEL_POSE_LANDMARKER_LITE = 1
        const val MODEL_POSE_LANDMARKER_HEAVY = 2
    }
}
