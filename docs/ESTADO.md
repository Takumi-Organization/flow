# Estado real (2026-11-13)

## Verificado
- `./gradlew :engine:test`: 43 pruebas JVM, 0 fallos (motor, validador, planificación, exportación, comparaciones, variables, disparadores).
- `:app` compila (comprobado antes de este commit tras el cambio en `Viewport.fit(readable=…)`).

## No verificado (requiere dispositivo o emulador; no se ha ejecutado ninguno)
- Aspecto y gestos del lienzo, incluida la escala 0,75 de `Viewport.fit(readable=…)`.
- `DeviceTest.kt` (prueba instrumentada) no ejecutada.
- Receptores reales: alarmas exactas, WiFi (NetworkCallback), Bluetooth, servicio de escucha (batería, carga, auriculares), reprogramación tras reinicio.
- Acciones de plataforma: notificación, sonido, temporizador, abrir app, calendario, No molestar, volumen, en distintas versiones de Android y con permisos denegados.
- Persistencia Room y migraciones en dispositivo; selector de ficheros de exportar/importar.
- Que las plantillas de la biblioteca pasen el validador con el registro completo de Android (el motor valida los nodos propios, las plantillas viven en `:app`).

## Falta
- Disparador por ubicación y disparador por evento de calendario (no se incluyen para no aparentar funcionar).
- Pruebas de interfaz y de las plantillas en JVM.

Ver [REQUISITOS.md](REQUISITOS.md) para la tabla completa.
