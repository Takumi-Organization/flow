#!/bin/bash
# Capturas de las pantallas principales con datos de ejemplo.
# Uso: scripts/capturas.sh <serial> <prefijo> <tema: grafito|washi|amoled> [dir]
# Requiere el APK de depuración instalado y datos cargados con `DeviceSeedTest#seed` (-e seed true).
# El cerrojo del dispositivo (phone.lock / android.lock) corre a cargo del llamador.
S="${1:?serial}"; P="${2:?prefijo}"; TH="${3:-grafito}"; D="${4:-docs/capturas}"; A="adb -s $S"
read W H <<<"$($A shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | tr 'x' ' ')"
tab() { $A shell input tap $(( W * ($1 * 2 + 1) / 8 )) $(( H * 925 / 1000 )); sleep 1.6; }
shot() { $A exec-out screencap -p > "$D/$P-$1.png"; }
$A shell am force-stop dev.flow.app
printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <string name=\"theme\">%s</string>\n    <boolean name=\"onboarded\" value=\"true\" />\n</map>\n" "$TH" > /tmp/flow_state.xml
$A push /tmp/flow_state.xml /data/local/tmp/flow_state.xml >/dev/null
$A shell run-as dev.flow.app mkdir -p shared_prefs
$A shell run-as dev.flow.app cp /data/local/tmp/flow_state.xml shared_prefs/flow_state.xml
$A shell am start -n dev.flow.app/.MainActivity >/dev/null; sleep 4
shot inicio
$A shell input swipe $((W/2)) $((H*8/10)) $((W/2)) $((H*2/10)) 400; sleep 1.2; shot inicio-2
$A shell input swipe $((W/2)) $((H*8/10)) $((W/2)) $((H*2/10)) 400; sleep 1.2; shot inicio-3
tab 1; shot presets
tab 2; shot actividad
$A shell input tap $((W/2)) $((H*52/100)); sleep 2; shot ejecucion
$A shell input keyevent KEYCODE_BACK; sleep 1
tab 3; shot ajustes
$A shell input swipe $((W/2)) $((H*8/10)) $((W/2)) $((H*2/10)) 400; sleep 1.2; shot ajustes-2
$A shell input swipe $((W/2)) $((H*8/10)) $((W/2)) $((H*2/10)) 400; sleep 1.2; shot ajustes-3
$A shell am start -n dev.flow.app/.MainActivity --es flowId seed-night >/dev/null; sleep 3; shot editor
$A shell input tap $((W*14/100)) $((H*165/1000)); sleep 1.6; shot editor-prueba
