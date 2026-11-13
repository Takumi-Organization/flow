# FLOW

Automatización visual personal para Android: el usuario construye sus propios flujos
CUANDO (disparador) / SI (condición) / ENTONCES (acción) en un lienzo de nodos.

- `:engine` — Kotlin puro (sin Android): modelo, motor, validador, planificación, exportación versionada. Tiene pruebas JVM.
- `:app` — Compose + Room + WorkManager: editor, pantallas, acciones y receptores de Android.

## Compilar y probar

```
./gradlew :engine:test          # pruebas JVM del motor (43)
./gradlew :app:assembleDebug    # APK de depuración
```

Requiere JDK 17+ y el SDK de Android (`local.properties` con `sdk.dir`, no se versiona).
`app/src/androidTest/.../DeviceTest.kt` necesita dispositivo o emulador y **no se ha ejecutado** (ver docs/ESTADO.md).

## Documentación

- [docs/ESTADO.md](docs/ESTADO.md) — qué está hecho, qué se verificó y qué no.
- [docs/REQUISITOS.md](docs/REQUISITOS.md) — encargo original contrastado con el código.
- [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) — cómo añadir un nodo, mecanismos de ejecución, límites de Android.
