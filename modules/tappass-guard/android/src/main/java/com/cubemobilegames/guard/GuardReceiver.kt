package com.cubemobilegames.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Brings the store watcher back after a phone restart or an app update. */
class GuardReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val why = when (intent.action) {
      Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> "phone restarted"
      Intent.ACTION_MY_PACKAGE_REPLACED -> "app updated"
      else -> return
    }
    if (!Guard.enabled(context)) return
    Guard.start(context, why)
  }
}
