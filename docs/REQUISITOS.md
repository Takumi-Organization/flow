# Requisitos del encargo contrastados con el código

Estado a 2026-11-13. «Verificado» indica el nivel: **JVM** (pruebas de `:engine`), **emulador** (prueba instrumentada o captura en `emulator-5554`, Pixel API 36) o **código** (revisado y compilado, no ejecutado). Detalle en [ESTADO.md](ESTADO.md).

| Apartado | Requisito | Estado | Verificado |
|---|---|---|---|
| 1 | Reglas CUANDO / SI / ENTONCES | Hecho | JVM |
| 2 | Crear, conectar, eliminar, duplicar, configurar nodos; ramas ordenadas; condiciones | Hecho | JVM (motor) + emulador (arrastrar nodo, conectar con vista previa, selección múltiple, ramas 1/2 en capturas) |
| 2 | Zoom y desplazamiento | Hecho | emulador (botones +/−, arrastre del fondo, pellizco inyectado con UiAutomation: `pinchZoomsTheCanvas`) |
| 2 | Disparadores hora, fecha, día | Hecho | JVM (planificación) + emulador (alarma real programada y reprogramada) |
| 2 | Disparador WiFi, batería, carga | Hecho | JVM (matchers) + emulador (`dumpsys battery`, `svc wifi`: 5/5 eventos disparan flujos reales) |
| 2 | Disparador Bluetooth, auriculares con cable | Hecho | JVM (matchers). **Receptores reales no verificados**: requieren un dispositivo Bluetooth o un jack reales (el emulador no los simula) |
| 2 | Disparador de ubicación | Hecho | JVM (distancia, cruce, validación) + emulador (`emu geo fix` fuera y dentro de la zona: el flujo se ejecuta) |
| 2 | Disparador de evento de calendario | Hecho | JVM (selección del próximo aviso) + emulador (evento real insertado en CalendarContract, la alarma dispara el flujo con `{{trigger.title}}`) |
| 2 | Acción manual | Hecho | JVM + emulador (botón Probar) |
| 2 | Condiciones: hora, fecha, batería, conexión, día, variables, resultado de nodos | Hecho | JVM |
| 2 | Acciones: notificación, sonido, temporizador, registrar, abrir app, guardar información, leer calendario, No molestar, volumen | Hecho | emulador (`DeviceTest`, 9/9 con alarmas exactas concedidas) |
| 3 | Modelo, eventos, condiciones, ejecutor, errores, historial, reintentos, estado; extensible por `NodeRegistry` | Hecho | JVM (48 pruebas) |
| 4 | Room: automatizaciones, nodos, conexiones, config., variables, historial, ejecuciones | Hecho | emulador (round-trip, duplicar, borrar en cascada, historial) |
| 4 | Activar, desactivar, duplicar, editar, eliminar | Hecho | emulador (repositorio) + capturas |
| 4 | Exportar e importar, formato versionado | Hecho | JVM + emulador (`exportImportRoundTripInRealRoom`; exportación por el selector del sistema escribe un JSON válido de 5,9 kB). El selector de **importar** no se completó por la interfaz: el emulador compartido cambió de app durante la prueba; la lógica de importación sí está verificada |
| 4 | Migraciones de Room | N/A | La base está en la versión 1 y no hay migraciones que probar; el esquema se exporta (`exportSchema = true`) |
| 5 | Biblioteca: matutina, concentración, nocturno, batería, auriculares, Bluetooth, **antes de una reunión, llegar a un sitio** | Hecho | emulador (`everyTemplatePassesValidatorWithFullAndroidRegistry`: todas pasan el validador con el registro completo; captura de la Biblioteca) |
| 6 | Variables texto, número, booleano, fecha, hora, lista | Hecho | JVM + emulador (persistentes de los 6 tipos en Room) |
| 7 | Historial con fecha, hora, automatización, nodo, resultado, duración, error | Hecho | emulador (se registran las ejecuciones reales y se leen del repositorio) |
| 7 | Depuración visual con estados | Hecho | JVM (secuencia de estados) + emulador (vista previa de conexión resaltada; estados de ejecución sin captura dedicada) |
| 8 | Permisos y restricciones, alternativas, avisos | Hecho | emulador (No molestar sin acceso devuelve «no disponible»; abrir app en segundo plano cae a notificación; pantalla de permisos en capturas) |
| 9 | WorkManager, AlarmManager, receptores, servicio justificado, reinicio | Hecho | emulador (alarmas, receptores, servicio de escucha; reprogramación: `BootReceiver` ejecutado directamente y registro en el manifiesto comprobado). **Reinicio real del emulador no ejecutado** (comparte emulador con otros agentes) |
| 10 | Pantallas principal, editor, biblioteca, historial, ajustes | Hecho | emulador (capturas de las cuatro pestañas y el editor; modo claro Washi; pantalla 720x1280) |
| 11 | Tutorial, ayuda contextual, ejemplos, validación, nodos incompletos, avisos de permisos | Hecho | emulador (tutorial en primer arranque) + JVM (validador) |
| 12 | Sin bucles, recursión ni duplicados; límites y cancelación | Hecho | JVM |
| 13 | Sin nodos decorativos | Cumple | Todos los nodos ejecutan su API o devuelven `Unavailable` con la razón |
