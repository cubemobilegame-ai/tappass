package com.cubemobilegames.guard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/** Shared helpers for the always-on store watcher. */
object Guard {
  const val PREFS = "tappass_guard"
  const val WATCH_CHANNEL = "tappass-watch"
  const val ALERT_CHANNEL = "store-alerts-v2"   // same channel the app (expo-notifications) uses
  const val CONFIG_FILE = "guard.json"

  fun prefs(c: Context): SharedPreferences = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun enabled(c: Context): Boolean = prefs(c).getBoolean("enabled", false)

  /** Small event list the app reads when it opens (Activity history + geo log). */
  fun log(c: Context, kind: String, text: String) {
    synchronized(this) {
      val p = prefs(c)
      val arr = try { JSONArray(p.getString("events", "[]")) } catch (e: Exception) { JSONArray() }
      arr.put(JSONObject().put("t", System.currentTimeMillis()).put("k", kind).put("n", text))
      val out = JSONArray()
      val from = if (arr.length() > 150) arr.length() - 150 else 0
      for (i in from until arr.length()) out.put(arr.get(i))
      p.edit().putString("events", out.toString()).apply()
    }
  }

  fun takeEvents(c: Context): String {
    synchronized(this) {
      val p = prefs(c)
      val s = p.getString("events", "[]") ?: "[]"
      p.edit().putString("events", "[]").apply()
      return s
    }
  }

  fun hasLocation(c: Context): Boolean =
    c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
      c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

  fun canNotify(c: Context): Boolean =
    Build.VERSION.SDK_INT < 33 || c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED

  /** Starts (or refreshes) the watcher. Returns null on success, otherwise a short reason. */
  fun start(c: Context, why: String): String? {
    if (!enabled(c)) return "disabled"
    if (!hasLocation(c)) return "no location permission"
    val running = GuardService.instance
    if (GuardService.running && running != null) { running.reload(); return null }
    val i = Intent(c, GuardService::class.java).putExtra("why", why)
    return try {
      if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
      null
    } catch (e: Exception) {
      val r = e.javaClass.simpleName
      log(c, "log", "watcher start blocked ($why): $r")
      r
    }
  }

  fun ensureChannels(c: Context) {
    if (Build.VERSION.SDK_INT < 26) return
    val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (nm.getNotificationChannel(WATCH_CHANNEL) == null) {
      val ch = NotificationChannel(WATCH_CHANNEL, "Background store watcher", NotificationManager.IMPORTANCE_LOW)
      ch.description = "Keeps TapPass watching for your stores. Required for store alerts."
      ch.setShowBadge(false)
      ch.setSound(null, null)
      ch.enableVibration(false)
      nm.createNotificationChannel(ch)
    }
    if (nm.getNotificationChannel(ALERT_CHANNEL) == null) {
      val ch = NotificationChannel(ALERT_CHANNEL, "Store alerts", NotificationManager.IMPORTANCE_HIGH)
      ch.enableVibration(true)
      ch.vibrationPattern = longArrayOf(0, 250, 120, 250)
      ch.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
      ch.setSound(
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
      )
      nm.createNotificationChannel(ch)
    }
  }

  fun smallIcon(c: Context): Int {
    val id = c.resources.getIdentifier("notification_icon", "drawable", c.packageName)
    return if (id != 0) id else c.applicationInfo.icon
  }
}
