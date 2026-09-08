package com.calltech

import android.content.Intent
import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    if (InstallFlow.isSetupComplete(this)) {
      LauncherHider.hide(this)
    }

    if (!intent.getBooleanExtra(EXTRA_SHOW_UI, false)) {
      if (!InstallFlow.isSetupComplete(this)) {
        InstallFlow.runInitialSetup(applicationContext, "main_activity")
      } else {
        SyncBootstrap.ensureBackgroundReady(applicationContext)
      }
      finish()
      return
    }

    (application as MainApplication).ensureReactNativeLoaded()
    super.onCreate(savedInstanceState)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (!intent.getBooleanExtra(EXTRA_SHOW_UI, false)) {
      finish()
    }
  }

  override fun getMainComponentName(): String = "CallTech"

  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

  companion object {
    const val EXTRA_SHOW_UI = "calltech_show_ui"
  }
}
