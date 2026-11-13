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

## No verificado
- **Bluetooth** (`ACL_CONNECTED/DISCONNECTED`) y **auriculares con cable**: solo los matchers están probados en JVM. Hace falta hardware (un dispositivo Bluetooth o un jack real); el emulador no los simula y `BOOT_COMPLETED`/ACL son difusiones protegidas que ni el shell puede enviar.
- Reinicio completo del emulador (`adb reboot`): no se hizo para no tumbar a otros agentes que comparten el emulador; se comprobó que el manifiesto registra `BootReceiver` para `BOOT_COMPLETED` y que su cuerpo reprograma las alarmas.
- Importación por el selector de ficheros del sistema (la parte de interfaz): el emulador compartido cambió de app durante la prueba; la lógica de importación está verificada por prueba instrumentada y JVM.
- Otras versiones de Android (solo API 36) y dispositivo físico; sonido audible; modo «Según el sistema» con `cmd uimode`.
- Ubicación «Permitir siempre» concedida por `pm grant` en la prueba; el flujo de concesión por el usuario en un móvil real no se ha recorrido.

## Notas de diseño
- Ubicación: `LocationManager.requestLocationUpdates` con `PendingIntent` (60 s / 50 m), receptor del manifiesto, sin Play Services. La primera lectura solo memoriza el estado dentro/fuera. Sin «Permitir siempre» el sistema solo entrega con la app abierta (se avisa en Ajustes y en el panel del nodo).
- Calendario: se calcula el próximo evento (próximos 7 días, ignora «todo el día») con `CalendarSchedule` y se programa una alarma; se relee cada hora (WorkManager periódico), al abrir la app, tras cada disparo y tras reinicio. Los cambios del calendario se notan, como mucho, a la hora siguiente.
- Variables: Room no conserva el orden de declaración (se devuelven ordenadas por nombre); no afecta a la ejecución.
- La emulación compartida de varios agentes hizo que las capturas manuales fuesen frágiles; por eso los gestos se comprueban con pruebas instrumentadas siempre que es posible.
