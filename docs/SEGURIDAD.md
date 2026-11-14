# Seguridad: permisos y componentes del manifiesto

Revisión de 2026-11-14 sobre `app/src/main/AndroidManifest.xml`.

## Permisos (todos con uso real)
| Permiso | Para qué | Notas |
|---|---|---|
| `POST_NOTIFICATIONS` | Avisos, temporizadores, alternativa cuando Android bloquea abrir una app | En tiempo de ejecución |
| `SCHEDULE_EXACT_ALARM` | Disparadores de hora precisos | Sin él se degrada a alarma aproximada y se avisa. No se usa `USE_EXACT_ALARM` |
| `RECEIVE_BOOT_COMPLETED` | Reprogramar alarmas y escuchas tras reiniciar | |
| `FOREGROUND_SERVICE` + `_SPECIAL_USE` | Escucha de batería, carga, WiFi y auriculares con cable, solo con flujos activos que la necesiten | Subtipo declarado en el manifiesto |
| `READ_CALENDAR` | Acción «Leer próximos eventos» y disparador de calendario | En tiempo de ejecución |
| `BLUETOOTH_CONNECT` | Nombre del dispositivo Bluetooth | En tiempo de ejecución (API 31+) |
| `ACCESS_FINE_LOCATION` | Nombre de la red WiFi (Android lo oculta sin él) y disparador de ubicación | En tiempo de ejecución |
| `ACCESS_BACKGROUND_LOCATION` | Disparador de ubicación con la app cerrada | Se concede a mano; sin él solo funciona con la app abierta y se avisa |
| `ACCESS_NETWORK_STATE` | Estado de la red WiFi | |
| (sin `INTERNET`) | | Flow no puede enviar datos fuera del dispositivo |
| `WRITE_CALENDAR` | Solo en el manifiesto de depuración, para pruebas que crean eventos | No está en la versión de lanzamiento |

Cambios de esta revisión: se quitó `ACCESS_WIFI_STATE` (no se usa).

## Componentes
| Componente | Exportado | Motivo |
|---|---|---|
| `MainActivity` | sí | Lanzador. No procesa extras ni enlaces |
| `BootReceiver` | no | Solo recibe difusiones del sistema (protegidas); comprobado con un reinicio real del emulador |
| `BluetoothReceiver` | sí | El aviso ACL lo envía el proceso Bluetooth (otro uid). Son difusiones protegidas: una app no puede falsificarlas. Además el receptor descarta cualquier otra acción (antes un intent explícito sin acción contaba como «desconexión») |
| `AlarmReceiver`, `TimerReceiver`, `LocationReceiver` | no | Solo reciben `PendingIntent` propios |
| `ListenerService` | no | |

## Otros puntos revisados
- `PendingIntent` de alarmas y notificaciones: `FLAG_IMMUTABLE`. El de ubicación es `FLAG_MUTABLE` (el sistema añade la posición) pero lleva componente explícito.
- `allowBackup="false"`: ni los flujos ni el historial salen por copias de seguridad.
- Importar: el fichero se limita a 5 MB, se valida formato y versión, los ids se regeneran y los flujos entran desactivados; las plantillas `{{…}}` solo sustituyen valores, no ejecutan código.
- `queries` solo para listar apps con icono de lanzador (selector de «Abrir aplicación»); no se pide `QUERY_ALL_PACKAGES`.
- Pendiente de revisar por quien publique: firma de lanzamiento, ofuscación y la política de la tienda sobre ubicación en segundo plano.
