package com.cubemobilegames.guard

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Always-on store watcher. Runs as a location foreground service, compares every position with the
 * saved store list and posts the store alert itself, so it works with the app closed or killed.
 * Android restarts it after a reboot / app update (GuardReceiver) and after killing it (START_STICKY).
 */
class GuardService : Service() {
  companion object {
    private const val WATCH_ID = 7301
    private const val NEAR_ZONE = 1500.0          // m: closer than this to a store -> fast GPS
    @Volatile var running = false
    @Volatile var mode = -1                       // 0 = far (slow), 1 = near (fast)
    @Volatile var instance: GuardService? = null
  }

  /** New store list from the app while the watcher is already running (no service restart needed). */
  fun reload() {
    android.os.Handler(Looper.getMainLooper()).post { loadConfig() }
  }

  private var client: FusedLocationProviderClient? = null
  private var names: List<String> = emptyList()
  private var pts: IntArray = IntArray(0)
  private var ids: JSONObject = JSONObject()
  private var radius = 150.0
  private var cooldown = 4 * 3600_000L
  private var lastLog = 0L
  private var stoppedOnPurpose = false

  private val callback = object : LocationCallback() {
    override fun onLocationResult(result: LocationResult) {
      result.lastLocation?.let { onFix(it) }
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val why = intent?.getStringExtra("why") ?: "restarted by Android"
    if (!Guard.enabled(this) || !Guard.hasLocation(this)) {
      stoppedOnPurpose = true
      stopSelf()
      return START_NOT_STICKY
    }
    try {
      Guard.ensureChannels(this)
      val n = watchNotification()
      if (Build.VERSION.SDK_INT >= 29) startForeground(WATCH_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
      else startForeground(WATCH_ID, n)
    } catch (e: Exception) {
      Guard.log(this, "log", "watcher could not start ($why): ${e.javaClass.simpleName}")
      stoppedOnPurpose = true
      stopSelf()
      return START_NOT_STICKY
    }
    loadConfig()
    if (!running) {
      running = true
      instance = this
      Guard.prefs(this).edit().putLong("startedAt", System.currentTimeMillis()).apply()
      Guard.log(this, "log", "watcher started ($why), ${pts.size / 3} stores")
      request(1)                                   // fast first fix, then adapt
    }
    return START_STICKY
  }

  override fun onDestroy() {
    try { client?.removeLocationUpdates(callback) } catch (e: Exception) {}
    if (running && !stoppedOnPurpose && Guard.enabled(this)) Guard.log(this, "log", "watcher stopped by Android")
    running = false
    mode = -1
    instance = null
    super.onDestroy()
  }

  private fun loadConfig() {
    try {
      val f = File(filesDir, Guard.CONFIG_FILE)
      if (!f.exists()) return
      val j = JSONObject(f.readText())
      val n = j.getJSONArray("names")
      names = (0 until n.length()).map { n.getString(it) }
      val p = j.getJSONArray("pts")
      pts = IntArray(p.length()) { p.getInt(it) }
      ids = j.optJSONObject("ids") ?: JSONObject()
      radius = j.optDouble("radius", 150.0)
      cooldown = j.optLong("cooldown", 4 * 3600_000L)
    } catch (e: Exception) {
      Guard.log(this, "log", "store list unreadable: ${e.javaClass.simpleName}")
    }
  }

  @SuppressLint("MissingPermission")
  private fun request(m: Int) {
    val c = client ?: LocationServices.getFusedLocationProviderClient(this).also { client = it }
    val req = if (m == 1)
      LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
        .setMinUpdateIntervalMillis(5_000L).setMinUpdateDistanceMeters(10f).build()
    else
      LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60_000L)
        .setMinUpdateIntervalMillis(30_000L).setMinUpdateDistanceMeters(50f).build()
    try {
      c.removeLocationUpdates(callback)
      c.requestLocationUpdates(req, callback, Looper.getMainLooper())
      mode = m
    } catch (e: SecurityException) {
      Guard.log(this, "log", "location permission missing, watcher stopped")
      stoppedOnPurpose = true
      stopSelf()
    }
  }

  private fun dist(a: Double, b: Double, c: Double, d: Double): Double {
    val r = Math.PI / 180
    val h = sin((c - a) * r / 2).let { it * it } + cos(a * r) * cos(c * r) * sin((d - b) * r / 2).let { it * it }
    return 2 * 6371e3 * asin(sqrt(h))
  }

  private fun onFix(loc: Location) {
    val now = System.currentTimeMillis()
    val lat = loc.latitude
    val lng = loc.longitude
    val acc = min(loc.accuracy.toDouble(), 100.0)
    Guard.prefs(this).edit().putLong("lastFix", now).apply()

    var best = Double.MAX_VALUE
    var bestName = ""
    val hits = LinkedHashSet<String>()
    val dLat = 0.06                                // ~6.5 km quick filter
    val dLng = 0.06 / maxOf(0.1, cos(lat * Math.PI / 180))
    var i = 0
    while (i + 2 < pts.size) {
      val la = pts[i + 1] / 1e5
      val lo = pts[i + 2] / 1e5
      if (abs(la - lat) <= dLat && abs(lo - lng) <= dLng) {
        val d = dist(lat, lng, la, lo)
        val name = names.getOrNull(pts[i]) ?: ""
        if (d < best) { best = d; bestName = name }
        if (d <= radius + acc && name.isNotEmpty()) hits.add(name)   // GPS error counts in the user's favour
      }
      i += 3
    }

    val want = if (best < NEAR_ZONE) 1 else 0
    if (want != mode) request(want)

    if ((best < 500 && now - lastLog > 30_000) || now - lastLog > 15 * 60_000) {
      lastLog = now
      Guard.log(this, "log", "loc ±${acc.toInt()} m" + if (bestName.isNotEmpty()) ", nearest $bestName ${best.toInt()} m" else ", no store within 6 km")
    }

    if (hits.isEmpty()) return
    val p = Guard.prefs(this)
    val fresh = hits.filter { now - p.getLong("last:$it", 0L) >= cooldown }
    if (fresh.isEmpty()) return
    val e = p.edit()
    fresh.forEach { e.putLong("last:$it", now) }
    e.apply()
    fresh.forEach { Guard.log(this, "alert", it) }
    alert(fresh)
  }

  private fun watchNotification(): Notification {
    val launch = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
    val pi = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    @Suppress("DEPRECATION")
    val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, Guard.WATCH_CHANNEL) else Notification.Builder(this)
    b.setSmallIcon(Guard.smallIcon(this))
      .setContentTitle("TapPass")
      .setContentText("Watching for your stores nearby")
      .setColor(0xFF6F7285.toInt())
      .setOngoing(true)
      .setShowWhen(false)
      .setContentIntent(pi)
    if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
    return b.build()
  }

  private fun alert(found: List<String>) {
    if (!Guard.canNotify(this)) { Guard.log(this, "log", "notifications are off, alert not shown"); return }
    val cardIds = ArrayList<String>()
    for (n in found) {
      val a = ids.optJSONArray(n) ?: continue
      for (k in 0 until a.length()) cardIds.add(a.getString(k))
    }
    val uri = Uri.parse("tappass://card?ids=" + Uri.encode(cardIds.joinToString(",")))
    val open = Intent(Intent.ACTION_VIEW, uri).setPackage(packageName)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val code = (System.currentTimeMillis() % 100000).toInt()
    val pi = PendingIntent.getActivity(this, code, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val one = found.size == 1
    @Suppress("DEPRECATION")
    val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, Guard.ALERT_CHANNEL) else Notification.Builder(this)
    b.setSmallIcon(Guard.smallIcon(this))
      .setContentTitle(if (one) "📍 You are near ${found[0]}!" else "📍 ${found.size} of your stores are nearby")
      .setContentText(if (one) "Tap to open your card." else "Tap to choose your card.")
      .setAutoCancel(true)
      .setContentIntent(pi)
      .setVisibility(Notification.VISIBILITY_PUBLIC)
      .setCategory(Notification.CATEGORY_REMINDER)
    if (Build.VERSION.SDK_INT < 26) {
      @Suppress("DEPRECATION")
      b.setPriority(Notification.PRIORITY_MAX).setDefaults(Notification.DEFAULT_ALL)
    }
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.notify(8000 + code % 1000, b.build())
  }
}
