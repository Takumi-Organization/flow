package dev.flow.app.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.flow.app.MainActivity
import dev.flow.app.R
import dev.flow.engine.*
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

private val nid = AtomicInteger((System.currentTimeMillis() % 100000).toInt() + 1000)
private fun nextId() = nid.incrementAndGet()
private fun P(key: String, label: String, type: ParamType = ParamType.TEXT, required: Boolean = false, default: String = "",
              choices: List<Pair<String, String>> = emptyList(), help: String = "") = ParamSpec(key, label, type, required, default, choices, help)

object AppState { @Volatile var inForeground = false }

fun Context.has(perm: String) = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

fun notificationsBlocked(ctx: Context): String? =
    if (Build.VERSION.SDK_INT >= 33 && !ctx.has(Manifest.permission.POST_NOTIFICATIONS)) "Falta el permiso de notificaciones (Ajustes → Permisos)"
    else if (!ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) "Las notificaciones de Flow están desactivadas en Android"
    else null

fun launchIntent(ctx: Context) = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

/** Acciones que dependen de Android. Cada una comprueba permisos y restricciones y devuelve Unavailable con la razón. */
fun androidActions(ctx: Context): List<NodeSpec> {
    val app = ctx.applicationContext
    return listOf(
        NodeSpec.Action("notify", "Mostrar notificación", "Muestra una notificación. Admite {{variables}}.",
            listOf(P("title", "Título", required = true, default = "Flow"), P("text", "Texto", ParamType.MULTILINE, true, "Hola")),
            permissionHint = "notificaciones") { _, p ->
            notificationsBlocked(app)?.let { return@Action Outcome.Unavailable(it) }
            val n = NotificationCompat.Builder(app, Channels.MESSAGES).setSmallIcon(R.drawable.ic_flow)
                .setContentTitle(p["title"]).setContentText(p["text"]).setStyle(NotificationCompat.BigTextStyle().bigText(p["text"]))
                .setContentIntent(launchIntent(app)).setAutoCancel(true).build()
            app.getSystemService(NotificationManager::class.java).notify(nextId(), n)
            Outcome.Success(Value.Text("mostrada"))
        },
        NodeSpec.Action("play_sound", "Ejecutar sonido", "Reproduce un tono del sistema (respeta el modo silencio según el tipo).",
            listOf(P("kind", "Tono", ParamType.CHOICE, true, "notification", listOf("notification" to "Notificación", "alarm" to "Alarma", "ringtone" to "Tono de llamada")),
                P("seconds", "Duración máx. (s)", ParamType.NUMBER, true, "3")), timeoutMs = 40_000) { _, p ->
            val (type, usage) = when (p["kind"]) {
                "alarm" -> RingtoneManager.TYPE_ALARM to AudioAttributes.USAGE_ALARM
                "ringtone" -> RingtoneManager.TYPE_RINGTONE to AudioAttributes.USAGE_NOTIFICATION_RINGTONE
                else -> RingtoneManager.TYPE_NOTIFICATION to AudioAttributes.USAGE_NOTIFICATION
            }
            val secs = p["seconds"]!!.toDouble().coerceIn(1.0, 30.0)
            val uri = RingtoneManager.getActualDefaultRingtoneUri(app, type) ?: RingtoneManager.getDefaultUri(type)
            val r = RingtoneManager.getRingtone(app, uri) ?: return@Action Outcome.Failure("No hay tono disponible", false)
            r.audioAttributes = AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            r.play()
            try { delay((secs * 1000).toLong()) } finally { r.stop() }
            Outcome.Success(Value.Text("reproducido"))
        },
        NodeSpec.Action("start_timer", "Iniciar temporizador", "Cuenta atrás con notificación y aviso sonoro al terminar (aunque el móvil esté en reposo).",
            listOf(P("minutes", "Minutos", ParamType.NUMBER, true, "25"), P("label", "Nombre", default = "Temporizador")),
            permissionHint = "notificaciones") { _, p ->
            val mins = p["minutes"]!!.toDouble()
            if (mins <= 0 || mins > 24 * 60) return@Action Outcome.Failure("Duración fuera de rango (0–1440 min)", false)
            TimerSupport.start(app, p["label"].orEmpty().ifBlank { "Temporizador" }, (mins * 60_000).toLong())
        },
        NodeSpec.Action("open_app", "Abrir aplicación", "Abre una app. Si Flow no está en primer plano, Android lo bloquea y se muestra una notificación para abrirla con un toque.",
            listOf(P("package", "Paquete (p. ej. com.android.chrome)", required = true, help = "Elige una app instalada en el panel.")),
            permissionHint = "notificaciones (alternativa cuando Android bloquea el arranque en segundo plano)") { _, p ->
            val pkg = p["package"]!!.trim()
            val li = app.packageManager.getLaunchIntentForPackage(pkg) ?: return@Action Outcome.Failure("La app «$pkg» no está instalada o no se puede abrir", false)
            li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (AppState.inForeground) { app.startActivity(li); Outcome.Success(Value.Text("abierta")) }
            else {
                notificationsBlocked(app)?.let { return@Action Outcome.Unavailable("Android no permite abrir apps desde segundo plano y $it") }
                val label = app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(pkg, 0))
                val pi = PendingIntent.getActivity(app, pkg.hashCode(), li, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val n = NotificationCompat.Builder(app, Channels.MESSAGES).setSmallIcon(R.drawable.ic_flow)
                    .setContentTitle("Abrir $label").setContentText("Toca para abrir (Android bloquea abrirla automáticamente)").setContentIntent(pi).setAutoCancel(true).build()
                app.getSystemService(NotificationManager::class.java).notify(nextId(), n)
                Outcome.Success(Value.Text("notificación para abrir"))
            }
        },
        NodeSpec.Action("calendar_upcoming", "Leer próximos eventos", "Lee los eventos del calendario de las próximas horas y los deja en una variable (lista o texto).",
            listOf(P("hours", "Próximas horas", ParamType.NUMBER, true, "24"), P("max", "Máximo de eventos", ParamType.NUMBER, true, "5"),
                P("save", "Guardar en variable", ParamType.VARIABLE)), permissionHint = "calendario") { c, p ->
            if (!app.has(Manifest.permission.READ_CALENDAR)) return@Action Outcome.Unavailable("Falta el permiso de calendario (Ajustes → Permisos)")
            val now = System.currentTimeMillis(); val end = now + (p["hours"]!!.toDouble().coerceIn(1.0, 720.0) * 3_600_000).toLong()
            val max = p["max"]!!.toDouble().toInt().coerceIn(1, 50)
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { android.content.ContentUris.appendId(it, now); android.content.ContentUris.appendId(it, end) }.build()
            val out = ArrayList<String>()
            app.contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
                null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cur ->
                while (cur.moveToNext() && out.size < max) {
                    val t = cur.getString(0).orEmpty().ifBlank { "(sin título)" }
                    val b = java.time.Instant.ofEpochMilli(cur.getLong(1)).atZone(ZoneId.systemDefault())
                    out += if (cur.getInt(2) == 1) "Todo el día · $t" else "%02d:%02d · %s".format(b.hour, b.minute, t)
                }
            } ?: return@Action Outcome.Failure("No se pudo leer el calendario")
            val v = Value.Items(out)
            p["save"]?.takeIf { it.isNotBlank() }?.let { name ->
                when (c.flow.variables.firstOrNull { it.name == name }?.type) {
                    VarType.LIST -> c.setVar(name, v)
                    VarType.TEXT -> c.setVar(name, Value.Text(out.joinToString("\n").ifEmpty { "Sin eventos próximos" }))
                    else -> return@Action Outcome.Failure("La variable «$name» debe ser de tipo Lista o Texto", false)
                }
            }
            Outcome.Success(v)
        },
        NodeSpec.Action("set_dnd", "No molestar", "Cambia el filtro de interrupciones. Requiere que concedas «Acceso a No molestar» a Flow en Ajustes.",
            listOf(P("mode", "Modo", ParamType.CHOICE, true, "priority", listOf("off" to "Desactivar", "priority" to "Solo prioritarias", "alarms" to "Solo alarmas", "none" to "Silencio total"))),
            permissionHint = "acceso a No molestar") { _, p ->
            val nm = app.getSystemService(NotificationManager::class.java)
            if (!nm.isNotificationPolicyAccessGranted) return@Action Outcome.Unavailable("Falta el acceso a «No molestar» (Ajustes → Permisos)")
            nm.setInterruptionFilter(when (p["mode"]) {
                "off" -> NotificationManager.INTERRUPTION_FILTER_ALL; "alarms" -> NotificationManager.INTERRUPTION_FILTER_ALARMS
                "none" -> NotificationManager.INTERRUPTION_FILTER_NONE; else -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
            })
            Outcome.Success(Value.Text(p["mode"]!!))
        },
        NodeSpec.Action("set_volume", "Volumen del sistema", "Ajusta el volumen de un canal del sistema (multimedia, alarma, timbre). No controla el volumen interno de otras apps.",
            listOf(P("stream", "Canal", ParamType.CHOICE, true, "music", listOf("music" to "Multimedia", "alarm" to "Alarma", "ring" to "Timbre")),
                P("percent", "Volumen (%)", ParamType.NUMBER, true, "50"))) { _, p ->
            val am = app.getSystemService(AudioManager::class.java)
            val stream = when (p["stream"]) { "alarm" -> AudioManager.STREAM_ALARM; "ring" -> AudioManager.STREAM_RING; else -> AudioManager.STREAM_MUSIC }
            val pct = p["percent"]!!.toDouble().coerceIn(0.0, 100.0)
            try {
                am.setStreamVolume(stream, Math.round(am.getStreamMaxVolume(stream) * pct / 100).toInt(), 0)
                Outcome.Success(Value.Num(pct))
            } catch (e: SecurityException) {
                Outcome.Unavailable("Android no permite cambiar este volumen ahora (¿modo No molestar activo sin acceso concedido?)")
            }
        },
    )
}

object TimerSupport {
    const val ACTION_END = "dev.flow.TIMER_END"
    fun start(ctx: Context, label: String, ms: Long): Outcome {
        val endAt = System.currentTimeMillis() + ms
        val id = nextId()
        val am = ctx.getSystemService(AlarmManager::class.java)
        val i = Intent(ctx, dev.flow.app.platform.TimerReceiver::class.java).setAction(ACTION_END).putExtra("label", label).putExtra("id", id)
            .setData(android.net.Uri.parse("flow://timer/$id"))
        val pi = PendingIntent.getBroadcast(ctx, id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // Con permiso de alarmas exactas: setAlarmClock (exacta, aparece como alarma). Sin él Android lanza SecurityException:
        // se degrada a una alarma aproximada y se avisa en el resultado en vez de fallar o fingir precisión.
        val exact = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setAlarmClock(AlarmManager.AlarmClockInfo(endAt, launchIntent(ctx)), pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi)
        } catch (e: SecurityException) { return Outcome.Failure("Android no permite programar el aviso: ${e.message}", false) }
        if (notificationsBlocked(ctx) == null) {
            val n = NotificationCompat.Builder(ctx, Channels.MESSAGES).setSmallIcon(R.drawable.ic_flow).setContentTitle(label)
                .setContentText("En marcha").setUsesChronometer(true).setChronometerCountDown(true).setWhen(endAt).setShowWhen(true)
                .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(launchIntent(ctx)).build()
            ctx.getSystemService(NotificationManager::class.java).notify(id, n)
        }
        val t = java.time.Instant.ofEpochMilli(endAt).atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0)
        return Outcome.Success(Value.Text(if (exact) "termina a las $t" else "termina hacia las $t (aproximado: concede «Alarmas exactas» en Ajustes)"))
    }
}
