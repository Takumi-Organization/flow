#!/bin/bash
# Verificación en emulador de lo que necesita estímulos externos. Uso: scripts/verificar-emulador.sh [serial]
# Requiere un emulador ya arrancado (no lo arranca) y compila con gradlew. Si varios agentes comparten el
# emulador, envuelve la llamada en un flock: `flock ~/.cache/claude-tmp/android.lock scripts/verificar-emulador.sh`.
set -u
S="${1:-emulator-5554}"; A="adb -s $S"; cd "$(dirname "$0")/.."
./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx1g :app:assembleDebug :app:assembleDebugAndroidTest -q || exit 1
$A install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
$A install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
R=dev.flow.app.test/androidx.test.runner.AndroidJUnitRunner

echo "== Temporizador con alarmas exactas concedidas"
$A shell appops set dev.flow.app SCHEDULE_EXACT_ALARM allow
$A shell am instrument -w -e class dev.flow.app.DeviceTest#timerSchedulesRealAlarmAndOngoingNotification $R | tail -3

echo "== Receptores de batería, carga y WiFi (dumpsys battery / svc wifi)"
$A shell dumpsys battery set ac 0; $A shell dumpsys battery set usb 0; $A shell dumpsys battery set level 70
$A shell am instrument -w -e ext true -e class dev.flow.app.DeviceListenersTest $R > /tmp/flow-ext.out 2>&1 &
sleep 20
$A shell dumpsys battery set level 40; sleep 3
$A shell dumpsys battery set ac 1; sleep 3
$A shell dumpsys battery set ac 0; sleep 3
$A shell svc wifi disable; sleep 8
$A shell svc wifi enable
wait; tail -3 /tmp/flow-ext.out; $A shell dumpsys battery reset

echo "== Disparador de ubicación (emu geo fix: fuera, luego dentro de la zona 37.5,-4.5 r=200 m)"
$A emu geo fix -4.7 37.9 >/dev/null
$A shell am instrument -w -e geo true -e class dev.flow.app.DeviceTriggersTest#locationTriggerFiresOnEnteringZone $R > /tmp/flow-geo.out 2>&1 &
sleep 25; $A emu geo fix -4.7 37.9 >/dev/null; $A emu geo fix -4.5 37.5 >/dev/null
sleep 45; $A emu geo fix -4.5001 37.5001 >/dev/null
wait; tail -3 /tmp/flow-geo.out

if [ "${REINICIO:-0}" = "1" ]; then
  echo "== Reinicio real: preparar flujos activos, anotar estado, reiniciar y comparar (solo con el emulador en exclusiva)"
  $A shell pm grant dev.flow.app android.permission.ACCESS_BACKGROUND_LOCATION
  $A shell am instrument -w -e prep true -e class dev.flow.app.DeviceRebootPrepTest#prepare $R | tail -2
  $A shell am start -n dev.flow.app/.MainActivity >/dev/null; sleep 3   # la app no debe quedar en estado «detenida»
  est() { echo "-- alarmas de Flow:"; $A shell dumpsys alarm | grep -E "RTC.*dev.flow.app|dev.flow.app.*(RTC|flow://alarm)|tag=.*dev.flow" | cut -c1-200; echo "-- servicio:"; $A shell dumpsys activity services dev.flow.app | grep -E "ServiceRecord.*ListenerService|isForeground" | cut -c1-160; echo "-- ubicación:"; $A shell dumpsys location | grep -E "dev.flow.app/.*Request|dev.flow.app: min" | cut -c1-200; }
  echo "antes:"; est
  $A reboot; $A wait-for-device; until [ "$($A shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 3; done; sleep 20
  echo "despues (sin abrir la app):"; est
  $A shell am instrument -w -e cleanup true -e class dev.flow.app.DeviceRebootPrepTest#cleanup $R | tail -2
fi
