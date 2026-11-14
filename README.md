# FLOW

Automatización visual personal para Android: el usuario construye sus propios flujos
CUANDO (disparador) / SI (condición) / ENTONCES (acción) en un lienzo de nodos.

- `:engine` — Kotlin puro (sin Android): modelo, motor, validador, planificación, exportación versionada. Tiene pruebas JVM.
- `:app` — Compose + Room + WorkManager: editor, pantallas, acciones y receptores de Android.

## Compilar y probar

```
./gradlew :engine:test          # pruebas JVM del motor (48)
./gradlew :app:testDebugUnitTest # pruebas JVM del catálogo de presets (6)
./gradlew :app:assembleDebug    # APK de depuración
```

Requiere JDK 17+ y el SDK de Android (`local.properties` con `sdk.dir`, no se versiona).
Las pruebas instrumentadas (`:app:connectedDebugAndroidTest`, emulador API 36) cubren acciones, Room, alarmas, calendario real y pellizco; `scripts/verificar-emulador.sh` ejecuta además las que necesitan estímulos externos (batería, WiFi, ubicación). Ver docs/ESTADO.md.

## Presets
70 plantillas listas para usar en 10 categorías (sueño y noche, trabajo y estudio, coche, batería, auriculares y Bluetooth, casa y WiFi, ubicación y horarios, privacidad, pantalla y sonido, rutinas). Se definen en `app/.../ui/Templates.kt` y `PresetsExtra.kt` y solo usan nodos que existen. La pestaña Presets tiene búsqueda, categorías con contador, filtro «sin permisos especiales» y paginación. Ver [docs/ESTADO.md](docs/ESTADO.md#presets-2026-11-15).

## Documentación

- [docs/ESTADO.md](docs/ESTADO.md) — qué está hecho, qué se verificó y qué no.
- [docs/REQUISITOS.md](docs/REQUISITOS.md) — encargo original contrastado con el código.
- [docs/SEGURIDAD.md](docs/SEGURIDAD.md) — permisos, receptores exportados y revisión de seguridad.
- [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) — cómo añadir un nodo, mecanismos de ejecución, límites de Android.

Diseño: sistema propio (Inter, esquinas continuas, tríada coral/violeta/menta, acento elegible) y capturas de las pantallas en [docs/capturas/](docs/capturas/), con el detalle en [docs/ESTADO.md](docs/ESTADO.md#rediseño-visual-2026-11-14).

## Estado de instalación
Instalada en el móvil 78af40b4 el 2026-11-15 (debug); arranca sin crash. Bluetooth, auriculares y ubicación en hardware real sin verificar. Comando en [docs/ESTADO.md](docs/ESTADO.md#instalación-en-el-móvil).
