package dev.flow.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import dev.flow.app.platform.Scheduler
import dev.flow.app.platform.has

/** Capacidad/permiso que Flow puede necesitar. [runtime] = se pide con diálogo; si no, se abre una pantalla de Ajustes. */
class Cap(val id: String, val name: String, val why: String, val runtime: String? = null, val granted: (Context) -> Boolean, val settings: ((Context) -> Intent)? = null)

val Caps: List<Cap> = listOf(
    Cap("notif", "Notificaciones", "Mostrar avisos, temporizadores y alternativas cuando Android bloquea una acción.",
        runtime = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null,
        granted = { c -> (Build.VERSION.SDK_INT < 33 || c.has(Manifest.permission.POST_NOTIFICATIONS)) && c.getSystemService(NotificationManager::class.java).areNotificationsEnabled() },
        settings = { c -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName) }),
    Cap("exact", "Alarmas exactas", "Disparar horas con precisión al minuto. Sin esto Android puede retrasar los disparadores de hora varios minutos.",
        granted = { c -> Scheduler.canExact(c) },
        settings = { c -> if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${c.packageName}")) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}")) }),
    Cap("calendar", "Calendario (lectura)", "Leer próximos eventos para la rutina matutina.", runtime = Manifest.permission.READ_CALENDAR, granted = { c -> c.has(Manifest.permission.READ_CALENDAR) }),
    Cap("dnd", "Acceso a «No molestar»", "Cambiar el modo No molestar. Android exige concederlo a mano en Ajustes.",
        granted = { c -> c.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted },
        settings = { Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }),
    Cap("bt", "Dispositivos Bluetooth cercanos", "Recibir cuándo se conecta o desconecta un dispositivo Bluetooth y conocer su nombre.",
        runtime = if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else null,
        granted = { c -> Build.VERSION.SDK_INT < 31 || c.has(Manifest.permission.BLUETOOTH_CONNECT) }),
    Cap("loc", "Ubicación", "Nombre de la red WiFi (Android lo oculta sin este permiso) y disparador de ubicación. Flow no envía tu posición a ningún sitio.",
        runtime = Manifest.permission.ACCESS_FINE_LOCATION, granted = { c -> c.has(Manifest.permission.ACCESS_FINE_LOCATION) }),
    Cap("bgloc", "Ubicación «Permitir siempre»", "Necesaria para que el disparador de ubicación funcione con la app cerrada. Sin ella solo se detecta con Flow abierta.",
        granted = { c -> Build.VERSION.SDK_INT < 29 || c.has(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
        settings = { c -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}")) }),
    Cap("battery", "Sin restricción de batería", "Evita que el ahorro agresivo de algunos fabricantes retrase alarmas y tareas.",
        granted = { c -> c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName) },
        settings = { Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }),
)

/** Permisos que necesita cada tipo de nodo para funcionar bien. */
fun capsFor(type: String): List<String> = when (type) {
    "notify", "start_timer", "open_app" -> listOf("notif")
    "calendar_upcoming" -> listOf("calendar")
    "set_dnd" -> listOf("dnd")
    "trigger_bluetooth" -> listOf("bt")
    "trigger_wifi" -> listOf("loc")
    "trigger_location" -> listOf("loc", "bgloc")
    "trigger_calendar" -> listOf("calendar")
    "trigger_time", "trigger_weekday", "trigger_date" -> listOf("exact")
    else -> emptyList()
}

@Composable
fun rememberCapRequester(onResult: () -> Unit = {}): (Cap) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Cap?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Si el usuario ya lo denegó dos veces Android no muestra el diálogo: se abre la pantalla de la app.
        val c = pending
        if (!it && c != null && c.runtime != null) runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }
        onResult()
    }
    return { cap ->
        pending = cap
        when {
            cap.runtime != null -> launcher.launch(cap.runtime)
            cap.settings != null -> runCatching { ctx.startActivity(cap.settings.invoke(ctx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
