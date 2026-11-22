
## 1. Política de Configuración y Tokens (Modelo en 3 Niveles)

### Nivel 1 · Parámetros Operativos (`assets/config.json` — Runtime + Remoto)
Queda **prohibido** escribir inline en código Kotlin valores operativos de red, tiempos o negocio:
- Intervalos de sondeo (`pollDelayMs`, escalones adaptativos), periodo de `Ticker`, polling visible.
- Umbrales de frescura (`FRESH`, `STALE`, `EXPIRED`) y tamaño de historial de `LiveFeeds`.
- Reglas de avisos (`triggerBufferSec`, `expirationDays`, rangos de `minutesAhead`, ventanas de avisos recurrentes, tope de badge `9+`).
- Timeouts HTTP, caché remota, velocidad de caminata (`walkingSpeedKmh`) y límites de ítems en `HomeScreen`.

**Regla de inyección:** `domain/` nunca accede a `assets/` ni `Context`. Las funciones puras reciben sub-objetos tipados (`PollingConfig`, `FreshnessConfig`, `AlertsConfig`) con validación defensiva en `ConfigRepository`.

### Nivel 2 · Animaciones y Tokens Visuales (`MotionTokens` / `DesignTokens` — Compile-Time)
Queda **prohibido** declarar inline duraciones (`tween(200)`), fracciones de desplazamiento, escalas (`0.97f`), ángulos (`72f`), constantes de *spring*, parámetros de *stagger*, opacidades de pulso o espaciados/radios estándar.
- Todos estos valores se consumen desde `MotionTokens` y `DesignTokens` (inicialmente en `ui/theme/` y a partir de v1.1 generados en *build-time* desde `packages/contracts/design-tokens.json`).
- **No crear parsers JSON en runtime para estilos o animaciones** (protege tiempo de arranque, R8 inlining y `@Preview`).

### Nivel 3 · Excepciones Permitidas Inline
- Identidades matemáticas (`0`, `1`, `0f`, `1f`), índices de listas.
- Micro-ajustes ópticos locales irrepetibles (`1.dp` hairline, `2.dp` ajuste óptico de fuente).
- Constantes de protocolo inmutables de la API Dbus (`tipo == 1.0`, `"DATOS"`, `"SIN_SERVICIO"`).

### Regla "Boy Scout" Acotada (Anti-Explosión de Diffs)
- Si encuentras valores inline de Nivel 1 o Nivel 2 **dentro de los archivos que estás modificando para tu tarea actual**, extráelos a `config.json` o `MotionTokens`/`DesignTokens` y añade/actualiza el test correspondiente.
- **No hagas barridos globales** en archivos no relacionados con tu tarea actual salvo que la tarea sea explícitamente un ticket de auditoría/refactorización.

---

## 2. Regla UX Obligatoria: Cero Códigos Internos o Numéricos Opacos en UI

La interfaz está diseñada para personas en la calle, nunca para exponer claves de base de datos ni de API:
1. **Nunca mostrar códigos de parada (`stopId`) en UI:** No renderizar `stopId`, `StopCodePill` ni números de poste en tarjetas, filas de lista, timelines, cabeceras, banners, notificaciones o placeholders de búsqueda. Identificar siempre las paradas por su **nombre legible**, sus **líneas**, su **distancia a pie** o su **sentido/destino** (en caso de nombres homónimos).
   - *Uso interno intacto:* `stopId` permanece únicamente en capa interna (`Room`, `DbusClient`, `LazyColumn` `key`, `sharedElement` `key`, rutas y deep links `dbus://stop/{stopId}`). En búsqueda puede aceptarse coincidencia silenciosa en segundo plano sin mostrar el número al usuario.
2. **Nunca mostrar IDs internos de línea ni de itinerario:** Prohibido renderizar el ID interno de Dbus (ej. `32` para la línea pública `28`), IDs técnicos de sentido/itinerario o identificadores numéricos no reconocibles en la calle. Mostrar **exclusivamente el código público oficial** (`28`, `5`, `B4`, `B9`) en `LineBadge` y el **destino legible**.

---

## 3. Reglas de Rendimiento y Preparación KMP (iOS)

1. **Cero I/O en Main Thread:** Nunca leer `assets/`, `filesDir` ni Room en el hilo principal.
2. **Aislar el `Ticker` de 30 s:** No pasar `nowMs` a pantallas completas; usar `distinctUntilChanged()` en ViewModel o lambdas hacia composables hoja.
3. **Animaciones en fase de dibujo:** Leer estados animados dentro de `Modifier.graphicsLayer { ... }` o `Modifier.offset { ... }`, nunca disparando recomposición de filas de `LazyColumn`.
4. **Estabilidad de listas:** Todo `items(...)` en `LazyColumn` debe declarar `key` estable y `contentType`.
5. **Accesibilidad y Robolectric:** Toda animación respeta `LocalReducedMotion` (`Settings.Global.ANIMATOR_DURATION_SCALE == 0f`). Cualquier `rememberInfiniteTransition` debe condicionarse a `!reducedMotion && !LocalInspectionMode.current` para no bloquear `waitForIdle()` en tests.
6. **Pureza KMP en `domain/`:** Prohibido añadir nuevos imports `java.time.*`, `java.util.Locale`, `java.text.*` o `String.format()` dentro de `domain/`. Usar `Long` (epoch ms UTC) o `kotlinx.datetime` y formatear cadenas únicamente en `ui/`.

---

## 4. Checklist Obligatorio Antes de Entregar PR / Commit
- [ ] Ningún `tween(número)`, `delay(número)` ni constante operativa inline introducida en el diff.
- [ ] Ningún `stopId`, `StopCodePill`, ID interno de línea ni código técnico visible en la UI o notificaciones.
- [ ] Ningún texto o `contentDescription` hardcodeado (`HardcodedText = ERROR` + paridad `values/` y `values-eu/`).
- [ ] Ningún import `java.time.*` o Android en `domain/`.
- [ ] `./gradlew check assembleDebug` ejecutado y en verde (incluyendo lint y todos los tests JVM/Robolectric).