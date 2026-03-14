package com.margelo.nitro.poselandmarks

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
    context = reactContext
    return null
  }

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider = ReactModuleInfoProvider { emptyMap() }

  companion object {
    var context: ReactApplicationContext? = null

    init {
      PoseLandmarksOnLoad.initializeNative()
    }
  }
}
