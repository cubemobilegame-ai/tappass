package com.cubemobilegames.guard

import android.content.Context
import android.content.Intent
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.io.File

class GuardModule : Module() {
  private val ctx: Context
    get() = appContext.reactContext ?: throw IllegalStateException("React context lost")

  override fun definition() = ModuleDefinition {
    Name("TapPassGuard")

    // store list: {"names":[...], "pts":[nameIndex, lat*1e5, lng*1e5, ...], "ids":{name:[cardId]}, "radius":150, "cooldown":ms}
    Function("configure") { json: String ->
      File(ctx.filesDir, Guard.CONFIG_FILE).writeText(json)
      true
    }

    // "" = started, otherwise the reason it could not start
    Function("start") {
      val c = ctx
      Guard.prefs(c).edit().putBoolean("enabled", true).apply()
      Guard.start(c, "app") ?: ""
    }

    Function("stop") {
      val c = ctx
      Guard.prefs(c).edit().putBoolean("enabled", false).apply()
      c.stopService(Intent(c, GuardService::class.java))
      true
    }

    Function("status") {
      val p = Guard.prefs(ctx)
      mapOf(
        "enabled" to p.getBoolean("enabled", false),
        "running" to GuardService.running,
        "mode" to GuardService.mode,
        "lastFix" to p.getLong("lastFix", 0L).toDouble(),
        "startedAt" to p.getLong("startedAt", 0L).toDouble()
      )
    }

    Function("takeEvents") {
      Guard.takeEvents(ctx)
    }
  }
}
