package com.poselandmarks

import android.util.Log

class HybridPoseLandmarks : com.margelo.nitro.poselandmarks.HybridPoseLandmarks() {
  init {
    Log.d(TAG, "com.poselandmarks.HybridPoseLandmarks initialized")
  }

  companion object {
    private const val TAG = "HybridPoseLandmarksBridge"
  }
}
