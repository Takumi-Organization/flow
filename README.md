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

Diseño: identidad Takumi (ver abajo) y capturas de las pantallas en [docs/capturas/](docs/capturas/), con el detalle en [docs/ESTADO.md](docs/ESTADO.md#rediseño-visual-2026-11-14).

## Estado de instalación
Instalada en el móvil 78af40b4 el 2026-11-15 (debug); arranca sin crash. Bluetooth, auriculares y ubicación en hardware real sin verificar. Comando en [docs/ESTADO.md](docs/ESTADO.md#instalación-en-el-móvil).

## Identidad visual: Takumi (2026-11-22)
Sustituye a Ryoku Glass. Negro `#000` y tinta `#F2F2F2` (claro «papel» `#F4F4F4`), monocromo estricto, esquinas rectas con corte diagonal arriba a la derecha (también en los nodos del lienzo), Outfit + JetBrains Mono, seleccionado por inversión, sin cristal ni desenfoque. Los estados se leen por glifo y texto (✓ ▲ ✕ ■), no por color; disparador, condición y acción se distinguen por forma y luminancia. La preferencia de acento se conserva solo por compatibilidad y ya no se muestra. Tokens en `takumi-design`.

Los tres colores de datos del lienzo (disparador, condición, acción) y los tonos por flujo no cambian: son como los colores de línea de Glass, semántica y no decoración. `Squircle` traduce sus radios a la escala Glass en un solo punto (`glassRadius`). El **cristal** (`ui/Glass.kt`, desenfoque real con `GraphicsLayer` + `BlurEffect`) solo está en la barra de pestañas, que ahora flota sobre el contenido; con API < 31 (minSdk 26) es opaca.

Capturas: `docs/capturas/glass-*.png` (emulador Pixel_API36). Sin verificar: móvil físico, fluidez del desenfoque, el editor con flujos complejos y los diálogos/hojas inferiores tras el cambio, y el contraste del texto sobre cristal con script. Tests: unitarios y lint en verde; 16 de 23 instrumentados (los 7 de `Device*` fallan igual en `main`: dependen del hardware/estado del emulador).
