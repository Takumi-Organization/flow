# Fuentes empaquetadas

**Inter** (Rasmus Andersson), bajo SIL Open Font License 1.1 (texto en `LICENSE.txt`). Se usan instancias estáticas de la fuente variable `InterVariable`, recortadas a latín con `pyftsubset` (≈46 kB cada una):

- Texto (tamaño óptico 14): regular 400, medium 500, semibold 600 → `inter_text_*.ttf`.
- Display (tamaño óptico 32): semibold 600, bold 700, extrabold 800 → `inter_display_*.ttf`. Para títulos y cifras grandes, con cifras tabulares.

Origen: paquete `inter-font` del sistema (`/usr/share/fonts/inter/InterVariable.ttf`), instanciado con `fonttools varLib.instancer`.
