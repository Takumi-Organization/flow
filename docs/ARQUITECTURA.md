# Arquitectura

## Motor (`:engine`)
- `Model.kt`: `FlowDef` / `NodeDef` / `EdgeDef` / `VarDef` (serializables). Los nodos condición tienen puertos `true`/`false`; el resto `out`.
- `Spec.kt`: `NodeSpec` (Trigger / Condition / Action) + `NodeRegistry`. **Añadir un tipo de nodo = registrar un `NodeSpec`**; el editor, el validador, la ejecución y la exportación lo recogen sin más cambios. `Environment` abstrae el estado del dispositivo.
- `Engine.kt`: `FlowEngine.run/dispatch`. Recorrido en profundidad, un nodo una vez por ejecución, ramas en orden de arista. Estados `PENDING/RUNNING/COMPLETED/ERROR/SKIPPED` emitidos a `RunListener` (historial y depuración visual).
- Fiabilidad: reintentos con retroceso exponencial (solo fallos recuperables), `Unavailable` (Android lo impide) sin reintento, timeout por acción, límite de nodos (200) y de tiempo (10 min), cancelación cooperativa (`CANCELLED`), anti-duplicado (1 s), un flujo no se ejecuta dos veces a la vez, profundidad máxima 3 en `run_flow` y bloqueo de recursión.
- `Validator.kt`: duplicados, parámetros obligatorios/tipados, variables, ciclos, puertos, nodos sin conectar, avisos de permisos. Un flujo con errores no se activa ni se ejecuta.
- `Schedule.kt`: próxima activación de disparadores de hora/día/fecha (puro, probado). `Export.kt`: formato `flow-export` v1, ids remapeados, importación siempre desactivada.

## App (`:app`)
- Room: flows, nodes, edges, variables, valores persistentes, runs, run_nodes, event_log (con poda).
- Ejecución por disparador: hora/fecha/día → AlarmManager (exacta si hay permiso) + WorkManager; WiFi → NetworkCallback con PendingIntent; Bluetooth → BroadcastReceiver del manifiesto; batería/carga/auriculares con cable → `ListenerService` en primer plano, solo mientras haya flujos activos que lo necesiten; reinicio/cambio de hora → `BootReceiver` reprograma.
- Acciones: notificación, sonido, temporizador, abrir app (alternativa por notificación en segundo plano), leer calendario, No molestar, volumen del sistema, esperar, variables, registrar evento, ejecutar otro flujo. Todas ejecutan la API real y devuelven `Unavailable` con la razón si falta permiso o Android lo bloquea.
- No se ofrece (Android lo impide a apps normales): activar/desactivar WiFi, Bluetooth o datos; volumen interno de otras apps; abrir apps en segundo plano sin toque.
