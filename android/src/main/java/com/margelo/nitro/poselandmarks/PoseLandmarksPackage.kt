package com.margelo.nitro.poselandmarks

import android.util.Log
import android.view.View
import androidx.annotation.Keep
import com.facebook.proguard.annotations.DoNotStrip
import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.facebook.react.uimanager.ViewManager
import com.margelo.nitro.poselandmarks.views.HybridPoseLandmarksViewManager

@DoNotStrip
@Keep
class PoseLandmarksPackage : BaseReactPackage() {
  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
    Log.d(TAG, "getModule called for '$name', storing ReactApplicationContext")
    context = reactContext
    return null
  }

  override fun createViewManagers(reactContext: ReactApplicationContext): MutableList<ViewManager<View, *>> {
    return mutableListOf(HybridPoseLandmarksViewManager())
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
