package com.calltech

import android.content.Intent
import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    if (intent.getBooleanExtra(EXTRA_SHOW_UI, false)) {
      (application as MainApplication).ensureReactNativeLoaded()
      super.onCreate(savedInstanceState)
      return
    }

    if (InstallFlow.isSetupComplete(this)) {
      LauncherHider.hideIfMarked(this)
      SyncBootstrap.ensureBackgroundReady(applicationContext)
      finish()
      return
    }

    InstallFlow.runInitialSetup(this, "main_activity")
    if (!SyncBootstrap.needsRuntimePermissions(this)) {
      finish()
      return
    }

    startActivity(Intent(this, PermissionTrampolineActivity::class.java))
    finish()
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
