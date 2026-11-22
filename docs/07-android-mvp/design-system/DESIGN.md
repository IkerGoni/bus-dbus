---
name: Donostia Transit UI — Android MVP v1
version: 2.0.0
platform: Android (Material Design 3)
languages: [es, eu]
accessibility: WCAG AA (>= 4.5:1)
touchTargetMinDp: 48
colors:
  light:
    background: '#F8FAFC'
    surface: '#FFFFFF'
    surface-variant: '#F1F5F9'
    border: '#E2E8F0'
    on-surface: '#0F172A'
    on-surface-variant: '#475569'
    outline: '#64748B'
    primary: '#005BB5'
    primary-hover: '#004890'
    on-primary: '#FFFFFF'
    primary-container: '#EFF6FF'
    on-primary-container: '#005BB5'
    live: '#059669'
    live-container: '#D1FAE5'
    on-live-container: '#065F46'
    scheduled: '#64748B'
    scheduled-container: '#F1F5F9'
    warning: '#D97706'
    warning-container: '#FEF3C7'
    on-warning-container: '#92400E'
    error: '#DC2626'
    on-error: '#FFFFFF'
    favorite: '#F59E0B'
  dark:
    background: '#0B1120'
    surface: '#1E293B'
    surface-variant: '#273549'
    border: '#334155'
    on-surface: '#F1F5F9'
    on-surface-variant: '#94A3B8'
    outline: '#64748B'
    primary: '#60A5FA'
    primary-hover: '#3B82F6'
    on-primary: '#082F49'
    primary-container: '#1E3A5F'
    on-primary-container: '#DBEAFE'
    live: '#34D399'
    live-container: '#064E3B'
    on-live-container: '#A7F3D0'
    scheduled: '#94A3B8'
    scheduled-container: '#334155'
    warning: '#FBBF24'
    warning-container: '#451A03'
    on-warning-container: '#FDE68A'
    error: '#F87171'
    on-error: '#450A0A'
    favorite: '#FBBF24'
typography:
  headline-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 1.5rem
    fontWeight: '700'
    lineHeight: 2rem
  title-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 1rem
    fontWeight: '700'
    lineHeight: 1.5rem
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 0.875rem
    fontWeight: '500'
    lineHeight: 1.25rem
  caption-uppercase:
    fontFamily: Plus Jakarta Sans
    fontSize: 0.75rem
    fontWeight: '600'
    lineHeight: 1rem
    letterSpacing: 0.05em
  countdown-hero:
    fontFamily: JetBrains Mono
    fontSize: 1.875rem
    fontWeight: '800'
    lineHeight: 2rem
    fontVariantNumeric: tabular-nums
  countdown-row:
    fontFamily: JetBrains Mono
    fontSize: 1rem
    fontWeight: '700'
    lineHeight: 1.25rem
    fontVariantNumeric: tabular-nums
  code-badge:
    fontFamily: JetBrains Mono
    fontSize: 0.75rem
    fontWeight: '600'
    lineHeight: 1rem
    fontVariantNumeric: tabular-nums
rounded:
  badge: 0.5rem
  button: 0.75rem
  card: 1rem
  sheet: 1.5rem
  pill: 9999px
spacing:
  xs: 0.25rem
  sm: 0.5rem
  md: 1rem
  lg: 1.5rem
  xl: 2rem
---

# Donostia Transit UI — Sistema de Diseño Unificado (Android MVP v1)

Sistema visual unificado para **Dbus Donostia** en Android. Reconcilia las
exportaciones de Stitch (`Inicio` y `Paradas`), elimina la contradicción previa
entre el frontmatter (`#f8f9ff`/`#00448a`) y el cuerpo (`#F8FAFC`/`#005BB5`),
añade modo oscuro completo y bilingüismo ES/EU, y retira menciones a elementos
inexistentes en la API de Dbus o diferidos a v2 (mapa embebido, saldo Mugi,
ocupación, accesibilidad por vehículo y andenes).

## 1. Principios visuales
- **Vistazo en 1 segundo:** jerarquía clara para consultar tiempos con una sola
  mano caminando o bajo lluvia.
- **Superficies limpias con bordes definidos:** fondo `#F8FAFC` (claro) /
  `#0B1120` (oscuro) y tarjetas `#FFFFFF` / `#1E293B` con borde `1px solid`
  (`#E2E8F0` / `#334155`) y radio fijo `16dp` (`1rem`).
- **Color estrictamente semántico:** verde esmeralda (`#059669`) solo para datos
  en tiempo real (`EN VIVO`); azul (`#005BB5`) para acciones primarias y pestaña
  activa; ámbar (`#F59E0B`) para favoritos y alertas silenciadas; colores
  oficiales Dbus exclusivamente en badges de línea.
- **Bilingüe ES / EU al 100%:** cero literales hardcodeados en UI.

## 2. Tipografía y regla de separación
- **Plus Jakarta Sans** (fallback `Roboto, system-ui, sans-serif`): obligatoria
  para **todos** los textos de interfaz, títulos, nombres de parada, destinos,
  botones, chips y etiquetas de la barra inferior.
- **JetBrains Mono** (`tabular-nums`): reservada **exclusivamente** a cifras y
  códigos (`3 min`, `10:48`, `340`, `1281`, números de línea). Nunca usar
  monoespaciada en frases ni en las pestañas de navegación.
- **Protección contra roturas de línea:** todos los tiempos (`3 min`), códigos
  de parada (`1281`), etiquetas de sección (`CERCA DE TI`) y etiquetas de la
  barra inferior llevan `whitespace-nowrap` / `maxLines = 1`. Los nombres de
  parada o destino largos se truncan con elipsis (`truncate`) sin empujar los
  minutos.

## 3. Badges oficiales de líneas Dbus
Cuadrado redondeado (`28×28dp` en listas, `36×36dp` en Hero Card, radio `8dp` /
`12dp`), texto en negrita tabular:
- **Color de fondo = `linea.color` que devuelve la API de Dbus en vivo**
  (ARGB con signo; p. ej. L27 = `-9144551` → `#747719`), guardado en local
  la primera vez que se ve.
- Si nunca se ha visto: fallback `#334155`.
- La lista fija de colores del paquete original se eliminó (2026-11-22): no
  coincidía con la API (L27 aparecía como `#005BB5`).
- Texto blanco o `#0F172A` según contraste ≥ 4.5:1 con el fondo.

## 4. Componentes clave y accesibilidad (WCAG AA)
- **Targets táctiles mínimos `48×48dp`:** obligatorios en estrella ★, campana 🔔,
  engranaje Ajustes, botón GPS, botones de tarjeta y pestañas inferiores.
- **Buscador pill único:** altura `48dp`, fondo `#FFFFFF`, borde exterior `1px
  solid #E2E8F0`, radio `9999px`, `<input>` interior sin borde ni foco nativo.
- **Tiempo real vs. Horario (WCAG — nunca solo color):**
  - En vivo: punto verde sólido (`6px` `#059669`) + cifra verde (`● 2 min`).
  - Horario: icono reloj gris (`16px` `#94A3B8`) + cifra gris (`🕒 9 min`).
  - Inminente (`< 60s`): `Ahora` / `Orain` en `#059669` negrita.
- **Estrellas de favoritos:** guardada = rellena (`FILL 1`, `#F59E0B`); no
  guardada = contorno (`FILL 0`, `#94A3B8`).
- **Navegación inferior unificada (Material 3 NavigationBar):**
  - 4 destinos fijos: `Inicio`, `Paradas`, `Líneas`, `Avisos` (badge `#DC2626`).
  - Indicador activo unificado: píldora `48×28dp` (`#EFF6FF` claro / `#1E3A5F`
    oscuro) envolviendo **solo el icono** con relleno (`FILL 1`) + etiqueta
    inferior en `Plus Jakarta Sans` (`11px`, `nowrap`).
- **Componentes de Alerta («Avísame X min antes»):**
  - Chips rápidos `2'`, `3'`, `5'`, `8'`, `10'`, `15'` sincronizados con slider `1–30`.
  - 5 estados visuales (`computeAlertStatus`): `TRIGGERED` (`⚡ Llegando`),
    `ACTIVE` (`● Activa`), `SNOOZED` (`⏱ Pospuesta`), `SILENCED` (`🌙 Silenciada`),
    `INACTIVE` (`Pausada`).
