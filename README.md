# FLOW

Automatización visual personal para Android: el usuario construye sus propios flujos
CUANDO (disparador) / SI (condición) / ENTONCES (acción) en un lienzo de nodos.

- `:engine` — Kotlin puro (sin Android): modelo, motor, validador, planificación, exportación versionada. Tiene pruebas JVM.
- `:app` — Compose + Room + WorkManager: editor, pantallas, acciones y receptores de Android.

## Compilar y probar

```
./gradlew :engine:test          # pruebas JVM del motor (48)
./gradlew :app:assembleDebug    # APK de depuración
```

Requiere JDK 17+ y el SDK de Android (`local.properties` con `sdk.dir`, no se versiona).
Las pruebas instrumentadas (`:app:connectedDebugAndroidTest`, emulador API 36) cubren acciones, Room, alarmas, calendario real y pellizco; `scripts/verificar-emulador.sh` ejecuta además las que necesitan estímulos externos (batería, WiFi, ubicación). Ver docs/ESTADO.md.

## Documentación

- [docs/ESTADO.md](docs/ESTADO.md) — qué está hecho, qué se verificó y qué no.
- [docs/REQUISITOS.md](docs/REQUISITOS.md) — encargo original contrastado con el código.
- [docs/SEGURIDAD.md](docs/SEGURIDAD.md) — permisos, receptores exportados y revisión de seguridad.
- [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) — cómo añadir un nodo, mecanismos de ejecución, límites de Android.
