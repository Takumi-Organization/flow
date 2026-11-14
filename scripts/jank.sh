#!/bin/bash
# Mide jank de Flow en un dispositivo: recorre pestañas y desplaza listas, y vuelca `dumpsys gfxinfo`.
# Uso: scripts/jank.sh <serial> [etiqueta]   (el llamador debe tener el cerrojo del dispositivo)
S="${1:?serial}"; TAG="${2:-medida}"; A="adb -s $S"
read W H <<<"$($A shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | tr 'x' ' ')"
tab() { $A shell input tap $(( W * ($1 * 2 + 1) / 8 )) $(( H * 925 / 1000 )); sleep 1.2; }
$A shell am force-stop dev.flow.app; $A shell am start -n dev.flow.app/.MainActivity >/dev/null; sleep 4
$A shell dumpsys gfxinfo dev.flow.app reset >/dev/null
for t in 0 1 2 3 0; do
  tab $t
  for i in 1 2 3; do $A shell input swipe $((W/2)) $((H*7/10)) $((W/2)) $((H*3/10)) 250; sleep 0.4; $A shell input swipe $((W/2)) $((H*3/10)) $((W/2)) $((H*7/10)) 250; sleep 0.4; done
done
echo "== $TAG ($S)"; $A shell dumpsys gfxinfo dev.flow.app | grep -E "Total frames|Janky frames|50th|90th|95th|99th|Number Missed Vsync|Number High input|Number Slow"
