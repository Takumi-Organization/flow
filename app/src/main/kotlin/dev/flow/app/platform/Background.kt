package dev.flow.app.platform

import android.app.*
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.*
import dev.flow.app.FlowApp
import dev.flow.app.R
import dev.flow.engine.*
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.ZoneId

private val mapSer = MapSerializer(String.serializer(), String.serializer())
private fun Context.graph() = (applicationContext as FlowApp).graph

/** Disparadores programados con AlarmManager. Cada alarma lleva (flowId,nodeId) y se recalcula tras cada disparo. */
object Scheduler {
    const val ACTION = "dev.flow.ALARM"
    suspend fun rescheduleAll(ctx: Context) {
        val g = ctx.graph(); val am = ctx.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        val desired = LinkedHashMap<String, Pair<String, LocalDateTime>>()
        for (f in g.repo.enabled()) for (n in f.nodes) if (Schedule.isScheduled(n.type)) {
            Schedule.nextFire(n, now)?.let { desired["${f.id}|${n.id}"] = n.id to it }
        }
        val prefs = g.prefs
        (prefs.alarmKeys - desired.keys).forEach { am.cancel(pi(ctx, it)) }
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        for ((key, v) in desired) {
            val at = v.second.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, key))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, key))
        }
        prefs.alarmKeys = desired.keys
    }
    private fun pi(ctx: Context, key: String): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION).setData(android.net.Uri.parse("flow://alarm/" + android.net.Uri.encode(key)))
            .putExtra("flowId", key.substringBefore('|')).putExtra("nodeId", key.substringAfter('|'))
        return PendingIntent.getBroadcast(ctx, key.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    fun canExact(ctx: Context) = Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}

/** Ejecuta flujos en WorkManager (sobrevive a la muerte del proceso; con cuota "expedited"). */
object Runner {
    fun scheduled(ctx: Context, flowId: String, nodeId: String) = enqueue(ctx, "run-$flowId-$nodeId", ExistingWorkPolicy.KEEP,
        workDataOf("kind" to "schedule", "flowId" to flowId, "nodeId" to nodeId))
    fun event(ctx: Context, type: String, data: Map<String, String>) = enqueue(ctx, null, ExistingWorkPolicy.APPEND,
        workDataOf("kind" to "event", "type" to type, "data" to Json.encodeToString(mapSer, data)))
    fun manual(ctx: Context, flowId: String) {
        enqueue(ctx, "manual-$flowId", ExistingWorkPolicy.KEEP, workDataOf("kind" to "manual", "flowId" to flowId))
    }
    private fun enqueue(ctx: Context, unique: String?, policy: ExistingWorkPolicy, data: Data) {
        val req = OneTimeWorkRequestBuilder<RunWorker>().setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
        val wm = WorkManager.getInstance(ctx)
        if (unique != null) wm.enqueueUniqueWork(unique, policy, req) else wm.enqueue(req)
    }
}

class RunWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, Channels.SERVICE).setSmallIcon(R.drawable.ic_flow)
            .setContentTitle("Ejecutando automatización").setOngoing(true).build()
        return ForegroundInfo(7001, n)
    }
    override suspend fun doWork(): Result {
        val g = applicationContext.graph(); val d = inputData
        when (d.getString("kind")) {
            "schedule" -> {
                val flow = g.repo.get(d.getString("flowId")!!)
                if (flow != null && flow.enabled) g.engine.run(flow, d.getString("nodeId")!!, TriggerEvent("schedule", mapOf("at" to LocalDateTime.now().toString())))
            }
            "manual" -> g.runManual(d.getString("flowId")!!).join()
            "event" -> g.engine.dispatch(TriggerEvent(d.getString("type")!!, Json.decodeFromString(mapSer, d.getString("data") ?: "{}")))
        }
        return Result.success()
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val flowId = i.getStringExtra("flowId") ?: return; val nodeId = i.getStringExtra("nodeId") ?: return
        val pending = goAsync()
        // Primero se arranca la ejecución y se reprograma la siguiente: si algo falla, no se pierde la serie.
        Runner.scheduled(ctx, flowId, nodeId)
        CoroutineScope(Dispatchers.Default).launch {
            try { Scheduler.rescheduleAll(ctx) } finally { pending.finish() }
        }
    }
}

/** Arranque, cambio de hora/zona y actualización de la app: se vuelven a programar alarmas y escuchas. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { ctx.graph().syncBackground(ctx) } finally { pending.finish() }
        }
    }
}

class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val dev = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                  else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val state = if (i.action == BluetoothDevice.ACTION_ACL_CONNECTED) "connected" else "disconnected"
        val name = dev?.safeName().orEmpty().ifBlank { dev?.address.orEmpty() }
        val prefs = ctx.graph().prefs
        val entry = "${dev?.address.orEmpty()}|$name"
        prefs.btConnected = if (state == "connected") prefs.btConnected + entry else prefs.btConnected.filterNot { it.startsWith(dev?.address.orEmpty() + "|") }.toSet()
        Runner.event(ctx, "bluetooth", mapOf("state" to state, "device" to name))
    }
}

class WifiReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val g = ctx.graph()
        val ssid = g.env.wifiSsid()
        val state = if (ssid != null) "connected" else "disconnected"
        if (g.prefs.wifiState == state && state == "disconnected") return
        g.prefs.wifiState = state
        Runner.event(ctx, "wifi", mapOf("state" to state, "ssid" to ssid.orEmpty()))
    }
    companion object {
        private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, 4242, Intent(ctx, WifiReceiver::class.java).setAction("dev.flow.WIFI"),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun sync(ctx: Context, needed: Boolean) {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val p = pi(ctx)
            try { cm.unregisterNetworkCallback(p) } catch (_: Exception) {}
            if (needed) cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), p)
        }
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val id = i.getIntExtra("id", 0); nm.cancel(id)
        if (notificationsBlocked(ctx) == null) {
            nm.notify(id + 1, NotificationCompat.Builder(ctx, Channels.TIMERS).setSmallIcon(R.drawable.ic_flow)
                .setContentTitle(i.getStringExtra("label") ?: "Temporizador").setContentText("Tiempo terminado")
                .setCategory(NotificationCompat.CATEGORY_ALARM).setContentIntent(launchIntent(ctx)).setAutoCancel(true).build())
        }
        try { RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.apply {
            audioAttributes = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build(); play()
            Handler5(this)
        } } catch (_: Exception) {}
    }
    private fun Handler5(r: android.media.Ringtone) { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ r.stop() }, 5000) }
}

/**
 * Servicio en primer plano OPCIONAL: solo existe mientras haya algún flujo activo con un disparador que Android
 * no permite recibir en segundo plano desde el manifiesto (batería, carga, auriculares con cable).
 */
class ListenerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastLevel = -1
    private val battery = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val level = if (l < 0 || s <= 0) return else l * 100 / s
            val prev = lastLevel; lastLevel = level; graph().prefs.lastBattery = level
            if (prev >= 0 && prev != level) fire(TriggerEvent("battery", mapOf("level" to "$level", "prev" to "$prev")))
        }
    }
    private val power = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            fire(TriggerEvent("charging", mapOf("state" to if (i.action == Intent.ACTION_POWER_CONNECTED) "connected" else "disconnected")))
        }
    }
    private val audio = object : AudioDeviceCallback() {
        private fun wired(d: Array<out AudioDeviceInfo>) = d.any { it.isSink && (it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_USB_HEADSET) }
        override fun onAudioDevicesAdded(d: Array<out AudioDeviceInfo>) { if (wired(d)) fire(TriggerEvent("headset", mapOf("state" to "connected"))) }
        override fun onAudioDevicesRemoved(d: Array<out AudioDeviceInfo>) { if (wired(d)) fire(TriggerEvent("headset", mapOf("state" to "disconnected"))) }
    }
    private fun graph() = (applicationContext as FlowApp).graph
    private fun fire(e: TriggerEvent) { scope.launch { graph().engine.dispatch(e) } }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = NotificationCompat.Builder(this, Channels.SERVICE).setSmallIcon(R.drawable.ic_flow).setContentTitle("Flow escucha eventos")
            .setContentText("Batería, carga o auriculares con cable tienen automatizaciones activas").setOngoing(true).setContentIntent(launchIntent(this)).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(7002, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(7002, n)
        if (!registered) {
            registered = true
            lastLevel = graph().prefs.lastBattery
            registerReceiver(battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            registerReceiver(power, IntentFilter().apply { addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED) })
            getSystemService(AudioManager::class.java).registerAudioDeviceCallback(audio, null)
        }
        return START_STICKY
    }
    private var registered = false
    override fun onDestroy() {
        if (registered) {
            runCatching { unregisterReceiver(battery) }; runCatching { unregisterReceiver(power) }
            runCatching { getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(audio) }
        }
        scope.cancel(); super.onDestroy()
    }
    override fun onBind(i: Intent?): IBinder? = null

    companion object {
        val TYPES = setOf("trigger_battery", "trigger_charging", "trigger_headset")
        /** Devuelve un aviso si Android no deja arrancar el servicio. */
        fun sync(ctx: Context, needed: Boolean): String? {
            val i = Intent(ctx, ListenerService::class.java)
            return if (needed) try { ctx.startForegroundService(i); null } catch (e: Exception) { "Android no permite iniciar el servicio de escucha ahora: ${e.message}" }
            else { ctx.stopService(i); null }
        }
    }
}
