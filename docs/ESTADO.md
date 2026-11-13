# Estado real (2026-11-13)

## Verificado
- `./gradlew :engine:test`: 43 pruebas JVM, 0 fallos (motor, validador, planificación, exportación, comparaciones, variables, disparadores).
- `:app` compila (comprobado antes de este commit tras el cambio en `Viewport.fit(readable=…)`).

- Emulador `emulator-5554` (Pixel API 36), 2026-11-13: `:app:assembleDebug` e instalación OK; `connectedDebugAndroidTest`: 9 pruebas, 8 pasan (notificación, volumen, No molestar sin acceso, temporizador degradado, alarma exacta del disparador de hora, abrir app desde segundo plano, calendario, Room duplicar/borrar/historial) y 1 omitida por supuesto (`timerSchedulesRealAlarmAndOngoingNotification`: requiere «Alarmas exactas» concedidas).
- Capturas en el emulador: pantalla de inicio y editor con «Rutina matutina» (flujo ancho). Con la escala legible, la vista inicial muestra ~2,5 nodos desde el disparador; los botones +/− y el desplazamiento funcionan (tras dos «−» se ven 4 nodos con texto aún legible). El flujo completo no cabe a la vez en la vista inicial: es el comportamiento de `fit(readable=true)`, no un fallo.

## No verificado (no probado ni en emulador ni en móvil)
- Gestos táctiles del lienzo (arrastrar, pellizcar, selección múltiple, conectar), modo claro y otros tamaños de pantalla.
- Temporizador con alarmas exactas concedidas (la prueba se omite).
- Receptores reales de eventos externos: WiFi (NetworkCallback), Bluetooth, servicio de escucha (batería, carga, auriculares), reprogramación tras reinicio.
- Acciones de plataforma en otras versiones de Android (solo API 36) y sonido real.
- Migraciones de Room; selector de ficheros de exportar/importar.
- Que las plantillas de la biblioteca pasen el validador con el registro completo de Android (el motor valida los nodos propios, las plantillas viven en `:app`).

## Falta
- Disparador por ubicación y disparador por evento de calendario (no se incluyen para no aparentar funcionar).
- Pruebas de interfaz y de las plantillas en JVM.

Ver [REQUISITOS.md](REQUISITOS.md) para la tabla completa.
