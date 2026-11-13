# Requisitos del encargo contrastados con el código

Leyenda: HECHO = implementado y, si es del motor, con prueba JVM · HECHO (sin verificar en dispositivo) = código completo pero no ejecutado en Android · FALTA.

| Apartado | Requisito | Estado | Evidencia / nota |
|---|---|---|---|
| 1 | Reglas CUANDO / SI / ENTONCES | HECHO | `Engine.kt`; `EngineTest` |
| 2 | Crear, conectar, eliminar, duplicar, configurar, reordenar nodos, ramas, condiciones | HECHO (UI sin verificar en dispositivo) | `EditorScreen.kt` (`addNode`, `connect`, `deleteSelection`, `duplicateSelection`, `moveEdge`); ramas y condiciones probadas en motor |
| 2 | Disparadores: hora, fecha, día, WiFi, Bluetooth, auriculares, batería, carga, manual | HECHO (receptores sin verificar en dispositivo) | `Builtins.kt`; matchers y planificación con pruebas JVM |
| 2 | Disparador de ubicación | FALTA | Exige geovalla/permiso de ubicación en segundo plano; sin nodo para no aparentar funcionar |
| 2 | Disparador de evento de calendario | FALTA | Solo existe la acción «Leer próximos eventos» (`calendar_upcoming`) |
| 2 | Condiciones: hora, fecha, batería, conexión, día, variables, resultado de nodos | HECHO | `cond_*`; `EngineTest`, `CoverageTest` |
| 2 | Acciones: notificación, sonido, temporizador, registrar, abrir app, guardar información, otras permitidas | HECHO (sin verificar en dispositivo) | `Actions.kt`, `Builtins.kt` |
| 3 | Modelo, eventos, condiciones, ejecutor, errores, historial, reintentos, estado | HECHO | `:engine`, 43 pruebas |
| 3 | Extensible sin reescribir | HECHO | `NodeRegistry`; las acciones de Android se registran desde la app |
| 4 | Room: automatizaciones, nodos, conexiones, config., variables, historial, ejecuciones | HECHO (sin verificar en dispositivo) | `Entities.kt`, `Dao.kt` |
| 4 | Activar, desactivar, duplicar, editar, eliminar, exportar, importar | HECHO (UI sin verificar) | `Repository.kt`, Ajustes; export/import con pruebas JVM |
| 4 | Formato de exportación versionado | HECHO | `flow-export` v1, rechaza versiones futuras |
| 5 | Biblioteca: matutina, concentración, nocturno, ahorro de batería, auriculares (+Bluetooth), editables | HECHO (plantillas no ejecutadas en dispositivo) | `Templates.kt`; la validación de plantillas con el registro real no tiene prueba JVM |
| 6 | Variables texto/número/booleano/fecha/hora/lista; crear, consultar, modificar, usar | HECHO | `Value.kt`; `CoverageTest` |
| 7 | Historial con fecha, hora, automatización, nodo, resultado, duración, error | HECHO (UI sin verificar) | `RunRow`/`RunNodeRow`, `RunDetailScreen` |
| 7 | Depuración visual con estados | HECHO en motor; lienzo sin verificar visualmente | `RunListener` probado; pintado de estados en `EditorCanvas.kt` |
| 8 | Investigar APIs, permisos y restricciones; alternativa; avisar | HECHO (sin verificar en dispositivo) | `Perms.kt`, `Actions.kt` (`Unavailable`), Ajustes «Lo que Android NO permite» |
| 9 | WorkManager, AlarmManager, receptores, servicio justificado, reinicio | HECHO (sin verificar en dispositivo) | `Background.kt`, manifiesto |
| 10 | Pantallas principal, editor (zoom, desplazamiento, selección múltiple), biblioteca, historial, ajustes | HECHO (sin verificar visualmente) | `ui/` |
| 11 | Tutorial, ayuda contextual, ejemplos, validación, nodos incompletos, avisos de permisos | HECHO | `Tutorial`, `Validator` (probado) |
| 12 | Sin bucles, recursión, duplicados; límites y cancelación | HECHO | `EngineTest` |
| 13 | Sin nodos decorativos | HECHO | Cada nodo ejecuta su API o devuelve `Unavailable`; por eso ubicación y calendario como disparador no existen |
