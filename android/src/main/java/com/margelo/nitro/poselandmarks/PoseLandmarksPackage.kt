package com.margelo.nitro.poselandmarks

import android.util.Log
import androidx.annotation.Keep
import com.facebook.proguard.annotations.DoNotStrip
import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfoProvider

@DoNotStrip
@Keep
class PoseLandmarksPackage : BaseReactPackage() {
  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
    Log.d(TAG, "getModule called for '$name', storing ReactApplicationContext")
    context = reactContext
    return null
  }

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider = ReactModuleInfoProvider { emptyMap() }

  companion object {
    private const val TAG = "PoseLandmarksPackage"
    var context: ReactApplicationContext? = null

    init {
      Log.d(TAG, "Companion init started, calling initializeNative()")
      PoseLandmarksOnLoad.initializeNative()
      Log.d(TAG, "Companion init finished, native library initialized")
    }
  }
}
