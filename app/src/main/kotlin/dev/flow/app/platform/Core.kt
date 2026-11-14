package dev.flow.app.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.BatteryManager
import dev.flow.engine.Environment
import java.time.LocalDateTime

object Channels {
    const val MESSAGES = "flow_messages"
    const val TIMERS = "flow_timers"
    const val SERVICE = "flow_service"
    fun create(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(MESSAGES, "Mensajes de automatizaciones", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(TIMERS, "Temporizadores", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(SERVICE, "Servicio de escucha", NotificationManager.IMPORTANCE_LOW))
    }
}

/** Estado pequeño que necesitan los receptores (sobrevive a la muerte del proceso). */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("flow_state", Context.MODE_PRIVATE)
    var btConnected: Set<String>
        get() = sp.getStringSet("bt", emptySet()).orEmpty()
        set(v) { sp.edit().putStringSet("bt", v).apply() }
    var wifiState: String?
        get() = sp.getString("wifi", null); set(v) { sp.edit().putString("wifi", v).apply() }
    var lastBattery: Int
        get() = sp.getInt("bat", -1); set(v) { sp.edit().putInt("bat", v).apply() }
    var alarmKeys: Set<String>
        get() = sp.getStringSet("alarms", emptySet()).orEmpty()
        set(v) { sp.edit().putStringSet("alarms", v).apply() }
    /** Último estado dentro/fuera ("in"/"out") por «flujo|nodo» del disparador de ubicación. */
    var geoStates: Map<String, String>
        get() = sp.getStringSet("geo", emptySet()).orEmpty().associate { it.substringBefore('=') to it.substringAfter('=') }
        set(v) { sp.edit().putStringSet("geo", v.map { "${it.key}=${it.value}" }.toSet()).apply() }
    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false); set(v) { sp.edit().putBoolean("onboarded", v).apply() }
    var theme: String
        get() = sp.getString("theme", "grafito") ?: "grafito"; set(v) { sp.edit().putString("theme", v).apply() }
    var accent: Int
        get() = sp.getInt("accent", 0); set(v) { sp.edit().putInt("accent", v).apply() }
}

class AndroidEnv(private val ctx: Context, private val prefs: Prefs) : Environment {
    override fun now(): LocalDateTime = LocalDateTime.now()

    private fun battery(): Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    override fun batteryLevel(): Int? = battery()?.let {
        val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (l < 0 || s <= 0) null else l * 100 / s
    }
    override fun isCharging(): Boolean? = battery()?.let { it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0 }

    @Suppress("DEPRECATION")
    override fun wifiSsid(): String? {
        // `transportInfo` llega en API 29; antes se devuelve null y las condiciones de SSID no se cumplen.
        if (android.os.Build.VERSION.SDK_INT < 29) return null
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        for (n in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val ssid = (caps.transportInfo as? WifiInfo)?.ssid?.trim('"')
            return if (ssid == null || ssid == "<unknown ssid>") "" else ssid
        }
        return null
    }
    override fun bluetoothDevices(): Set<String> = prefs.btConnected.map { it.substringAfter('|') }.toSet()
    override fun headsetConnected(): Boolean {
        val am = ctx.getSystemService(AudioManager::class.java)
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }
}

fun BluetoothDevice.safeName(): String = try { name ?: "" } catch (_: SecurityException) { "" }
