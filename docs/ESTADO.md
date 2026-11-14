# Estado real (2026-11-13)

## Verificado
- `./gradlew :engine:test`: 48 pruebas JVM, 0 fallos (motor, validador, planificación, exportación, variables, comparaciones, disparadores, distancia/cruce de zonas y planificación de calendario).
- Emulador `emulator-5554` (Pixel API 36), `connectedDebugAndroidTest`: todas pasan salvo tres que se omiten por diseño y se ejecutan aparte con `scripts/verificar-emulador.sh` porque necesitan estímulos externos (`-e ext true`, `-e geo true`, alarmas exactas). Ejecutadas aparte, también pasan:
  - **Ubicación**: `adb emu geo fix` fuera de la zona y luego dentro; el flujo se ejecuta y registra `Entrada en 37.5…`.
  - **Calendario**: evento real creado en CalendarContract; la alarma de «2 min antes» ejecuta el flujo con `{{trigger.title}}`.
  - **Batería, carga y WiFi**: `dumpsys battery set level/ac` y `svc wifi disable/enable` disparan 5 de 5 flujos reales (batería, carga on/off, WiFi on/off).
  - **Temporizador** con alarmas exactas concedidas (`appops set … SCHEDULE_EXACT_ALARM allow`).
  - **Pellizco** multitáctil real inyectado con UiAutomation sobre el lienzo.
  - Reprogramación de alarmas tras arranque (receptor ejecutado directamente), exportar/importar en Room real, y plantillas contra el registro completo de Android.
- Capturas en emulador: inicio, tutorial, biblioteca (modo oscuro y claro Washi), ajustes, editor; arrastrar nodo, conectar con vista previa, ramas 1/2, selección múltiple (`2 nodos · Duplicar · Eliminar · Limpiar`), pan y zoom por botones; pantalla 720x1280 (restaurada a 1080x2424); exportación por el selector del sistema (JSON válido).

## Cambios al verificar (defectos hallados y corregidos)
- **WiFi**: el sistema liberaba el `NetworkCallback` registrado con `PendingIntent` 5 s después de registrarlo (medido con `dumpsys connectivity`), así que nunca llegaba la desconexión. Ahora la escucha vive en `ListenerService` (como batería y carga). Verificado.
- `syncBackground` se serializa con un `Mutex` (dos sincronizaciones concurrentes podían dejar una escucha con el estado antiguo).
- El texto de Ajustes aún decía que ubicación y calendario estaban «pendientes»: eliminado.

## Verificado en la segunda ronda (2026-11-14)
- **Importar por el selector del sistema**: Ajustes → Exportar → Guardar escribe `flow-export.json` (5,9 kB); Importar → elegirlo muestra «Importados 1 flujos (desactivados)» y aparecen dos flujos en Inicio.
- **Reinicio real del emulador** (`adb reboot`, emulador en exclusiva, app sin abrir tras el reinicio): la alarma `reb-time` (03:33) reaparece en `dumpsys alarm` con el mismo `origWhen`; `ListenerService` vuelve a estar en primer plano; `dumpsys location` muestra los registros `fused` y `gps` creados tras el arranque. Esto prueba además el `BootReceiver` con `android:exported="false"`.
- **«Según el sistema»**: con `cmd uimode night no` la app se ve clara (Washi) y con `night yes` oscura (Grafito). Restaurado a `no`.
- **Bluetooth y auriculares** (en lo simulable): `BluetoothReceiver` ejecuta el flujo al recibir `ACL_CONNECTED` dentro del proceso y descarta una acción ajena; el evento `headset` recorre WorkManager, el motor y el flujo.
- **Calendario de pruebas**: se borra desde un test instrumentado (`testCalendarCanBeDeleted`) y al terminar la prueba de calendario; el emulador ya no tiene calendarios locales.
- `scripts/verificar-emulador.sh` (con `REINICIO=1` incluye el reinicio) deja temporizador, batería/carga/WiFi y ubicación en verde.

## No verificado
- **Hardware real**: el aviso `ACL_CONNECTED` del proceso Bluetooth del sistema y el `AudioDeviceCallback` con un auricular de cable o USB reales. El emulador no los simula y esas difusiones están protegidas (ni el shell puede enviarlas).
- **Otro nivel de API**: solo hay instalada la imagen `android-36 google_apis_playstore`; no se descargó ninguna otra. `minSdk` y comportamiento en Android 10–15 sin probar.
- Móvil físico: no se ha usado (regla del encargo).
- El flujo de conceder «Permitir siempre» a mano en un móvil real (en el emulador se concede con `pm grant`).
- Sonido audible.

## Notas de diseño
- Ubicación: `LocationManager.requestLocationUpdates` con `PendingIntent` (60 s / 50 m), receptor del manifiesto, sin Play Services. La primera lectura solo memoriza el estado dentro/fuera. Sin «Permitir siempre» el sistema solo entrega con la app abierta (se avisa en Ajustes y en el panel del nodo).
- Calendario: se calcula el próximo evento (próximos 7 días, ignora «todo el día») con `CalendarSchedule` y se programa una alarma; se relee cada hora (WorkManager periódico), al abrir la app, tras cada disparo y tras reinicio. Los cambios del calendario se notan, como mucho, a la hora siguiente.
- Seguridad: ver [SEGURIDAD.md](SEGURIDAD.md).
- Variables: Room no conserva el orden de declaración (se devuelven ordenadas por nombre); no afecta a la ejecución.
- La emulación compartida de varios agentes hizo que las capturas manuales fuesen frágiles; por eso los gestos se comprueban con pruebas instrumentadas siempre que es posible.

## Rediseño visual (2026-11-14)

- **Sistema**: papel lavanda y Grafito/AMOLED, neutros con matiz (nunca grises puros), tríada de datos a luminosidad pareja —disparador coral, condición violeta, acción menta— y acento de interfaz elegible (coral, rosa, violeta, ámbar, menta). Cada flujo recibe un tono propio estable.
- **Tipografía**: Inter (instancias de texto y display, cifras tabulares) con jerarquía nueva (mega/hero/num); se retira Komiki Mono. Licencia y recorte en [fuentes/LEEME.md](fuentes/LEEME.md).
- **Forma y movimiento**: esquinas continuas por superelipse, paneles con borde de luz, botones con presión elástica y hápticos por tipo de acción; transiciones de empuje entre pantallas, gesto de atrás desde el borde izquierdo con goma elástica y barra inferior flotante que se desvanece bajo el contenido. Todo el movimiento se anula con la escala de animaciones del sistema a 0.
- **Pantallas**: Inicio (héroe con cifras y flujos), Biblioteca de plantillas, Actividad, Ajustes, editor (lienzo, panel de nodo y presets) y tutorial con demostración animada del parche.
- **Navegación externa**: `launchMode="singleTop"` y extra `flowId`; una notificación abre el editor aunque la app ya esté abierta, sin apilar otra actividad.
- **Capturas**: 40 en [capturas/](capturas/) (grafito, washi y amoled × inicio 1–3, biblioteca, actividad, ejecución, ajustes 1–3, editor y editor con prueba) más las `antes-*` del diseño anterior. Se regeneran con `scripts/capturas.sh <serial> <prefijo> <tema>` tras sembrar datos con `DeviceSeedTest`.
- **Comprobado**: `:engine:test` (48), `:app:lintDebug` y `:app:assembleDebug` en verde; capturas revisadas y sin pantallas negras. `scripts/jank.sh` queda para medir en un dispositivo con el APK de release (el de depuración no es representativo).
