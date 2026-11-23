# Resumen de la remediación (A5-01…A5-18) — 2026-11-23

## Verificación final
- **Core (`@dbus/core`):** 43 tests (vitest, paridad + A5-02)
- **Bot (`@dbus/bot`):** 94 tests (tsc + biome + integración real con API Dbus)
- **Android debug:** 152 tests JVM (lint 0 errores)
- **Android release:** 146 tests JVM (lint 0 errores)
- **Build release (unsigned):** `check assembleDebug assembleRelease -Pdbus.release.unsigned=true` ✅
- **P0 A5-02** (filtro de destino): igualdad tras normalizar, sin contención (core, bot, Android, fixture compartido)
- **P0 A5-01** (token en historial): rotado por Iker, informe corregido, purga pendiente de decisión
- **P1 docs/fichas (A5-03/04/05/06/07):** fichas Play reescritas (es/eu), Sprint 5 → «parcial», recuentos reales 152/146, nombres de firma corregidos, límites Play cumplidos
- **P2 código (A5-08/09/10/11/12/13/16/18):** label launcher «(no oficial)», tope `maxWatchHours=3h`, constantes N1 en config, deep link validado, I/O fuera de Main, origen config detectable, `keystore.properties` ignorado
- **P3 UX/strings (A5-14/15/17):** «Parada o calle», atribución traducida, docs alineados con HEAD (Sprint 6 hecho)

## Gates de usuario pendientes
1. **Purga del historial** (`bot-code.zip`): `git filter-repo --invert-paths --path bot-code.zip --force` + force-push (irreversible; requiere orden explícita de Iker)
2. **Reinicio del bot en producción** con token nuevo + fix A5-02 (regla 9): pendiente de autorización (bot parado, sin proceso ni lock)
3. **Vídeo FGS**, **política de privacidad alojada**, **keystore de subida**, **icono definitivo**, **revisión euskaldun de la ficha eu**: dependen de Iker

## Archivos nuevos
- `docs/auditoria/2026-11-sprint5-remediacion.md` — plan y estado
- `docs/07-android-mvp/auditoria-secretos.md` — corregida (la original era falsa)
- `docs/07-android-mvp/play/ficha-play-es.md` / `ficha-play-eu.md` — reescritas
- Tests nuevos para A5-02, A5-09, A5-12, A5-16 en core, bot y Android

## Commits recomendados (Conventional Commits, español)
```bash
git add -A
git commit -m "fix(core,bot,android): A5-02 filtro de destino por igualdad, sin contención
A5-02: destinationsMatch usa igualdad tras normalizar (minúsculas, espacios fuera de - y /).
Fixture compartido domain-cases.json corregido + 4 destinos reales de L13 parada 103.
Tests en core, bot, Android.
Paridad core/bot/Android garantizada."

git add -A
git commit -m "fix(android): A5-09/10/13/16 config operativa, vigilancia, I/O, origen
- maxWatchHours=3h en AlertsConfig (tope de vigilancia, notifica y elimina)
- NO_SERVICE_STOP_MS / FETCH_SLACK_MS / MAX_LAST_KNOWN_AGE_NS → PollingConfig/GeoConfig
- SettingsStore y LocationProvider cargan en IO (cero I/O en Main)
- ConfigSource enum: DEFAULTS/BUNDLED/REMOTE_CACHE/REMOTE visible
- recurring* eliminados; recentStopsLimit en UiLimitsConfig"

git add -A
git commit -m "feat(android): A5-08/12/18 label no oficial, deep link seguro, gitignore
- android:label → app_launcher_name «Bus Donostia (no oficial) / (ez-ofiziala)»
- Deep link: stopId contra catálogo (stop_not_found), highlightLine solo líneas reales
- keystore.properties añadido a .gitignore"

git add -A
git commit -m "docs: A5-03/04/05/06/07/14/15/17 fichas Play, recuentos, strings, estado
- Fichas es/eu reescritas (límite 80, versión 1.1.0, categoría, vídeo, tope vigilancia)
- Sprint 5 → parcial; Sprint 6 fila añadida
- Test counts reales 152 debug / 146 release
- Búsquedas: «Parada o calle» sin códigos; atribución traducida
- Descargo explícito en A07; docs alineados con HEAD"
```

## Estado
**READY TO COMMIT**. Todas las verificaciones en verde. Gates de usuario documentados arriba.