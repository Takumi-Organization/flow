package dev.flow.app.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.CalendarContract
import androidx.work.*
import kotlinx.coroutines.launch
import dev.flow.app.FlowApp
import dev.flow.engine.*
import java.util.concurrent.TimeUnit

/** Lectura del calendario para el disparador «N minutos antes de un evento». */
object CalendarSupport {
    private const val HORIZON_DAYS = 7L

    /** Eventos que empiezan en los próximos días (vacío si no hay permiso). */
    fun upcoming(ctx: Context, from: Long): List<CalEvent> {
        if (!ctx.has(Manifest.permission.READ_CALENDAR)) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, from); android.content.ContentUris.appendId(it, from + HORIZON_DAYS * 86_400_000L)
        }.build()
        val out = ArrayList<CalEvent>()
        try {
            ctx.contentResolver.query(uri, arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
                null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext() && out.size < 500) out += CalEvent(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2), c.getInt(3) == 1)
            }
        } catch (_: SecurityException) {}
        return out
    }

    fun next(ctx: Context, node: NodeDef, after: Long): Pair<CalEvent, Long>? {
        val mins = (node.params["minutes"]?.toDoubleOrNull() ?: 10.0).toInt().coerceIn(0, 1440)
        return CalendarSchedule.next(upcoming(ctx, after), mins, node.params["title"].orEmpty(), after)
    }

    /** El calendario puede cambiar sin avisar a la app: se relee cada hora mientras haya flujos con este disparador. */
    fun syncPeriodic(ctx: Context, needed: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (needed) wm.enqueueUniquePeriodicWork("calendar-resync", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CalendarResyncWorker>(1, TimeUnit.HOURS).build())
        else wm.cancelUniqueWork("calendar-resync")
    }
}

class CalendarResyncWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result { Scheduler.rescheduleAll(applicationContext); return Result.success() }
}

/**
 * Disparador de ubicación con LocationManager + PendingIntent (sin Play Services). Android entrega posiciones al
 * receptor del manifiesto aunque el proceso no exista, siempre que haya permiso «Permitir siempre».
 * Intervalo largo y distancia mínima para gastar poca batería; el receptor decide por cada nodo si entró o salió.
 */
class LocationReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val locs = ArrayList<Location>()
        if (Build.VERSION.SDK_INT >= 33) {
            i.getParcelableArrayListExtra(LocationManager.KEY_LOCATIONS, Location::class.java)?.let { locs += it }
            i.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED, Location::class.java)?.let { locs += it }
        } else @Suppress("DEPRECATION") {
            i.getParcelableArrayListExtra<Location>(LocationManager.KEY_LOCATIONS)?.let { locs += it }
            i.getParcelableExtra<Location>(LocationManager.KEY_LOCATION_CHANGED)?.let { locs += it }
        }
        val loc = locs.lastOrNull() ?: return
        val pending = goAsync()
        val g = (ctx.applicationContext as FlowApp).graph
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { try {
            val states = g.prefs.geoStates.toMutableMap()
            for (f in g.repo.enabled()) for (n in f.nodes) if (n.type == "trigger_location") {
                val lat = n.params["lat"]?.toDoubleOrNull() ?: continue; val lon = n.params["lon"]?.toDoubleOrNull() ?: continue
                val radius = n.params["radius"]?.toDoubleOrNull() ?: 150.0
                val key = "${f.id}|${n.id}"
                val inside = Geo.distanceM(loc.latitude, loc.longitude, lat, lon) <= radius
                val prev = states[key]?.let { it == "in" }
                states[key] = if (inside) "in" else "out"
                if (Geo.crossed(prev, inside, n.params["transition"] ?: "enter"))
                    Runner.scheduled(ctx, f.id, n.id, "location", mapOf("lat" to "%.5f".format(java.util.Locale.US, loc.latitude), "lon" to "%.5f".format(java.util.Locale.US, loc.longitude), "state" to if (inside) "enter" else "exit"))
            }
            g.prefs.geoStates = states
        } finally { pending.finish() } }
    }
    companion object {
        private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, 4343, Intent(ctx, LocationReceiver::class.java).setAction("dev.flow.LOCATION"),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        /** Registra o retira las actualizaciones. Devuelve un aviso si no se puede (sin permiso, sin proveedor). */
        @SuppressLint("MissingPermission")
        fun sync(ctx: Context, needed: Boolean): String? {
            val lm = ctx.getSystemService(LocationManager::class.java)
            lm.removeUpdates(pi(ctx))
            if (!needed) { (ctx.applicationContext as FlowApp).graph.prefs.geoStates = emptyMap(); return null }
            if (!ctx.has(Manifest.permission.ACCESS_FINE_LOCATION)) return "Falta el permiso de ubicación: el disparador de ubicación no funcionará"
            val providers = listOf("fused", "gps", "network").filter { it in lm.allProviders && lm.isProviderEnabled(it) }
            if (providers.isEmpty()) return "La ubicación del dispositivo está desactivada"
            for (p in providers) try { lm.requestLocationUpdates(p, 60_000L, 50f, pi(ctx)) } catch (_: Exception) {}
            return if (Build.VERSION.SDK_INT >= 29 && !ctx.has(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                "Sin «Permitir siempre» el disparador de ubicación solo funciona con Flow abierta" else null
        }

        @SuppressLint("MissingPermission")
        fun lastKnown(ctx: Context): Pair<Double, Double>? {
            if (!ctx.has(Manifest.permission.ACCESS_FINE_LOCATION)) return null
            val lm = ctx.getSystemService(LocationManager::class.java)
            return lm.allProviders.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }?.let { it.latitude to it.longitude }
        }
    }
}
