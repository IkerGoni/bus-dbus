# Prompts de Google Stitch AI — Pantallas restantes (Android MVP v1)

Adjunta siempre `stitch-references/inicio/code.html` (o su captura limpia) como
referencia visual para mantener coherencia 100% con el sistema **Donostia Transit UI**:
- Canvas `#F8FAFC`, tarjetas `#FFFFFF` con borde `1px #E2E8F0` y radio `16dp`
- Primary `#005BB5`, Live `#059669`, Favorito `#F59E0B` (relleno)
- `Plus Jakarta Sans` en textos UI · `JetBrains Mono` (`tabular-nums`) solo en cifras/códigos
- Barra inferior fija de 4 destinos: `Inicio · Paradas · Líneas · Avisos` (sin Mapa)

---

## 1. A05 · Detalle de parada (`dbus://stop/{stopId}`)

```text
Using the attached Home screen as the exact visual style reference (same #F8FAFC canvas, #FFFFFF 16dp rounded cards with 1px #E2E8F0 borders, Plus Jakarta Sans UI typography, JetBrains Mono tabular numbers for times/codes, #005BB5 primary blue, #059669 live emerald), design the "Detalle de parada" screen (Android Material 3, 390x844).
STRICT RULES: NO embedded map, NO Mugi balance card, NO vehicle occupancy, NO fake speed/distance telemetry, ALL times/codes single-line (nowrap), 48dp minimum touch targets. Spanish UI copy.

Exact vertical structure:
1. Compact top app bar (56dp): Back arrow on left, bold "Pío Baroja 10" + subtle gray pill "340"; on right two 48dp icon buttons: external directions icon ("Cómo llegar") and solid filled gold star (#F59E0B).
2. Horizontal line filter pills below header: "Todas" (active #005BB5 pill, white text), plus clean white pills with colored line dots: "5", "16", "18".
3. Section header: uppercase "PRÓXIMAS LLEGADAS" on left, muted "Hace 10s" + refresh icon on right.
4. One clean white 16dp card with 4 spacious arrival rows separated by hairline #F1F5F9 dividers:
   - Row 1: Green square "5" (#52A535) | Bold "Benta Berri → Amara" & muted "10:48" | Emerald "● 3 min" (nowrap) | 48dp soft circular bell button 🔔 on right.
   - Row 2: Light-blue square "16" (#3898C4) | Bold "Igeldo" & muted "10:53" | Gray clock icon + "8 min" (nowrap) | 48dp circular bell button 🔔.
   - Row 3: Magenta square "18" (#C2007F) | Bold "Seminarioa" & muted "10:59" | Emerald "● 14 min" (nowrap) | 48dp circular bell button 🔔.
   - Row 4: Green square "5" (#52A535) | Bold "Benta Berri → Amara" & muted "11:03" | Emerald "● 18 min" (nowrap) | 48dp circular bell button 🔔.
5. Helper caption below card: "Toca la campana 🔔 junto a cualquier autobús para recibir un aviso antes de que llegue."
6. Sticky white bottom bar: single full-width #005BB5 primary button (52dp height, white bold text): "🔔 Crear aviso en esta parada". No bottom navigation bar.
```

---

## 2. A06 · Configurar Aviso — «Avísame X min antes» (`dbus://alert/new`)

```text
Using the attached Home screen as the exact visual style reference (same #F8FAFC canvas, #FFFFFF 16dp cards with 1px #E2E8F0 borders, Plus Jakarta Sans UI typography, JetBrains Mono tabular numbers, #005BB5 primary blue), design the "Nuevo aviso" screen (Android Material 3, 390x844) — the hero feature to notify the user X minutes before a bus arrives.
STRICT RULES: Uncluttered hierarchy, NO duplicate headers, NO monospace paragraphs, 48dp touch targets. Spanish UI copy.

Exact vertical structure:
1. Compact top app bar (56dp): Back arrow on left, bold "Nuevo aviso".
2. Compact white 16dp card for selected stop: pin icon + bold "Pío Baroja 10" + pill "340" on left; #005BB5 text button "Cambiar" on right.
3. Section label "LÍNEA": horizontal selectable chips for lines at this stop:
   - Selected chip: green square "5" (#52A535) + bold "Benta Berri" with #005BB5 border and #EFF6FF tint.
   - Unselected white chips: light-blue "16 Igeldo", magenta "18 Seminarioa".
4. Section label "AVISAR ANTES DE LA LLEGADA" inside a white 16dp card:
   - Centered bold #005BB5 mono headline: "5 min antes".
   - Row of 6 equal pill chips: "2'", "3'", "5'" (solid #005BB5, white text), "8'", "10'", "15'".
   - Clean horizontal Material 3 slider (1 to 30 min) positioned at 5.
5. Section label "FRECUENCIA" inside a white 16dp card:
   - Two-option segmented control: "[ Solo esta vez ]" | "[ Recurrente (active) ]".
   - Row of 7 circular day chips: "L M X J V" (solid #005BB5, white text) and "S D" (soft #F1F5F9).
   - Hairline divider + row: moon icon + "Silencio nocturno" + "23:00 – 07:00" + Material 3 switch ON.
6. Section label "ASÍ VERÁS LA NOTIFICACIÓN": compact Android notification preview card on #F1F5F9 surface:
   - Bus icon + "Dbus · ahora"
   - Bold "L5 → Amara llega en 5 min"
   - Muted "Pío Baroja 10 (340) · En vivo"
   - Two #005BB5 text actions: "Posponer 5'" and "Descartar".
7. Sticky bottom bar: full-width #005BB5 primary button (52dp, white bold text): "Guardar aviso". No bottom nav bar.
```

---

## 3. A03 · Catálogo de Líneas (`dbus://lines`)

```text
Using the attached Home screen as the exact visual style reference (same #F8FAFC canvas, #FFFFFF 16dp cards with 1px #E2E8F0 borders, Plus Jakarta Sans UI font, JetBrains Mono for line numbers, #005BB5 primary blue, and IDENTICAL 4-tab bottom navigation bar: Inicio, Paradas, Líneas, Avisos), design the "Líneas" screen (Android Material 3, 390x844).
STRICT RULES: Single clean top header (NO duplicate bar, NO user avatar), single seamless search pill (NO inner border), solid filled gold stars (#F59E0B), 48dp touch targets. Spanish UI copy.

Exact vertical structure:
1. Compact top bar (56dp): Bold "Líneas" on left, muted "39 líneas" on right.
2. Seamless white rounded search pill: magnifying glass + "Buscar número o destino (ej. 28, Ospitaleak)".
3. Three filter pills: "Todas" (active #EFF6FF tint, #005BB5 text), "Favoritas ★" (white), "Nocturnas" (white).
4. White 16dp card with spacious rows (58dp height, #F1F5F9 dividers) and official Dbus line squares:
   - Green "5" (#52A535) | "Benta Berri" | filled gold star + chevron.
   - Lavender "8" (#9B8BBF) | "Gros - Intxaurrondo" | outline star + chevron.
   - Teal "9" (#6E9E93) | "Egia - Intxaurrondo" | outline star + chevron.
   - Red "13" (#D9001B) | "Altza" | filled gold star + chevron.
   - Slate "14" (#5B7798) | "Bidebieta" | outline star + chevron.
   - Light-blue "16" (#3898C4) | "Igeldo" | outline star + chevron.
   - Cyan "17" (#008BBF) | "Gros - Amara - Miramón" | outline star + chevron.
   - Magenta "18" (#C2007F) | "Seminarioa" | outline star + chevron.
   - Blue "28" (#005BB5) | "Amara - Ospitaleak" | filled gold star + chevron.
5. Fixed Material 3 bottom navigation bar: Inicio, Paradas, Líneas (active #EFF6FF pill around icon), Avisos (red badge "2").
```

---

## 4. A03b · Itinerario de línea (`dbus://line/{lineId}`)

```text
Using the attached Home screen as the exact visual style reference (same #F8FAFC canvas, #FFFFFF 16dp cards, Plus Jakarta Sans UI font, JetBrains Mono for codes, #005BB5 primary blue), design the "Itinerario de línea" screen (Android Material 3, 390x844).
STRICT RULES: NO embedded map, NO fake telemetry, clean vertical stop timeline. Spanish UI copy.

Exact vertical structure:
1. Compact top app bar (56dp): Back arrow, solid #005BB5 square "28" + bold "Amara - Ospitaleak", and solid gold star (#F59E0B) on right.
2. Clean two-option segmented control: "[ → Ospitaleak (active #005BB5) ]" | "[ → Boulevard ]".
3. Section label "PARADAS DEL RECORRIDO (18)".
4. White 16dp card displaying a crisp vertical transit timeline on the left (#005BB5 2px line with circular stop nodes):
   - "Boulevard 15" + pill "1" + gold star | chevron.
   - "Urbieta 6" + pill "102" | chevron.
   - "Easo Plaza" + pill "108" | chevron.
   - "Sancho el Sabio 28" + pill "114" | chevron.
   - "Anoeta" + pill "122" | chevron.
   - "Ospitaleak" + pill "140" (solid terminal node) | chevron.
5. Helper caption below: "Toca una parada para ver llegadas en vivo o crear un aviso." No bottom nav bar.
```

---

## 5. A04 · Mis Avisos (`dbus://alerts`)

```text
Using the attached Home screen as the exact visual style reference (same #F8FAFC canvas, #FFFFFF 16dp cards with 1px #E2E8F0 borders, Plus Jakarta Sans UI font, JetBrains Mono for countdowns, #005BB5 primary blue, and IDENTICAL 4-tab bottom navigation bar: Inicio, Paradas, Líneas, Avisos), design the "Mis avisos" screen (Android Material 3, 390x844).
STRICT RULES: Single clean top header (NO duplicate bar, NO avatar), crisp state indicators, 48dp touch targets. Spanish UI copy.

Exact vertical structure:
1. Compact top bar (56dp): Bold "Mis avisos" on left, 48dp Settings gear icon on right.
2. Section label "ACTIVOS (2)":
   - Card 1 (Triggered, subtle emerald accent): Green square "5" + bold "Benta Berri → Amara" | bold emerald "● 3 min". Subtitle: "Pío Baroja 10 (340) · Aviso 5' · Solo esta vez". Bottom row: emerald "⚡ Llegando" on left; switch ON, edit pencil, trash icon on right.
   - Card 2 (Active): Blue square "28" + bold "Amara → Ospitaleak" | bold #005BB5 "11 min". Subtitle: "Boulevard 15 (1) · Aviso 8' · L–V". Bottom row: muted "● Activa" on left; switch ON, edit icon, trash icon on right.
3. Section label "SILENCIADOS Y PAUSADOS (2)":
   - Card 3 (Silenced): Red square "13" + bold "Altza". Subtitle: "Gros (412) · Aviso 5' · Diario". Bottom row: amber "🌙 Silenciada 23:00–07:00" on left; switch ON, edit, trash on right.
   - Card 4 (Paused, muted opacity): Cyan square "17" + "Miramón". Subtitle: "Loiola (1281) · Aviso 5'". Bottom row: gray "Pausada" on left; switch OFF on right.
4. Floating Action Button bottom-right above nav bar: rounded #005BB5 pill "+ Nuevo aviso" with white bold text.
5. Fixed Material 3 bottom navigation bar: Inicio, Paradas, Líneas, Avisos (active #EFF6FF pill around icon).
```

---

## 6. A07 · Ajustes (`dbus://settings`) y A08 · Onboarding

```text
Using the attached Home screen as the exact visual style reference (same #F8FAFC canvas, #FFFFFF 16dp cards with 1px #E2E8F0 borders, Plus Jakarta Sans UI font, #005BB5 accent), design the "Ajustes" screen (Android Material 3, 390x844).
STRICT RULES: Clean grouped settings cards, NO fake GTFS versions, NO fake marketing banners. Spanish UI copy.

Exact vertical structure:
1. Compact top bar (56dp): Back arrow + bold "Ajustes".
2. Card "PREFERENCIAS": Apariencia segmented "[ Sistema* | Claro | Oscuro ]" + Idioma segmented "[ Español* | Euskara ]".
3. Card "NOTIFICACIONES": Bell icon + "Permiso en Android" + emerald "● Activadas" + right button "Ajustes del sistema".
4. Card "DATOS Y CACHÉ": "Catálogo offline" ("548 paradas · 39 líneas"), "Última consulta en vivo" ("Hace 18 s"), "Caché local" ("420 KB" + outlined button "Limpiar").
5. Card "ACERCA DE": "Versión" ("1.0.0"), "Fuente de datos" ("Dbus / Open Data Euskadi"), "Licencia" ("MIT"). No bottom nav bar.
```

```text
Using the attached Home screen as the exact visual style reference (same #F8FAFC canvas, #FFFFFF 16dp cards, Plus Jakarta Sans UI font, #005BB5 accent), design a single-screen minimalist "Onboarding" welcome screen (Android Material 3, 390x844).
STRICT RULES: Single screen (NO carousel dots), NO embedded map mention, generous whitespace. Spanish UI copy.

Exact vertical structure:
1. Centered top brand mark: rounded #005BB5 app icon with white minimalist bus icon, bold "Dbus Donostia", muted subtitle "Llega a tu parada sin esperar bajo la lluvia."
2. Single clean white 16dp card with 3 crisp rows:
   - Emerald dot icon: "Paradas cercanas y favoritas en vivo" / "Consulta tiempos reales en menos de dos segundos."
   - Blue bell icon: "Avisos X minutos antes" / "Te notificamos justo a tiempo para salir hacia tu marquesina."
   - Globe/moon icon: "Sin anuncios · ES / EU" / "Rápida, ligera y con modo oscuro."
3. Bottom action area: full-width #005BB5 primary button (52dp, white bold text) "Permitir notificaciones y empezar", and subtle centered text button below "Empezar sin notificaciones".
```
