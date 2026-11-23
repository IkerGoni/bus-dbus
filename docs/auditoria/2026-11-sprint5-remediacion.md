# Remediación · auditoría adversarial Sprint 5 (A5-01…A5-18)

**Fecha:** 2026-11-23 · **Origen:** [`2026-11-sprint5-adversarial.md`](2026-11-sprint5-adversarial.md)
(veredicto NO SHIP: 2 P0, 5 P1, 6 P2, 5 P3). Este doc sigue el estado de cada
hallazgo; el informe de auditoría no se reescribe (es la evidencia).

## Gates de usuario (no ejecutables desde el repo)

| Acción | Estado |
|---|---|
| Rotar token en BotFather y actualizar `.env` | **Hecho por Iker** (verificado: el token de `.env` ya no coincide con el del zip; solo se comparó, nunca se imprimió) |
| Purga de `bot-code.zip` del historial (`git filter-repo` + force-push) | **Pendiente de decisión de Iker** (destructivo, irreversible; procedimiento abajo) |
| Reinicio del bot en producción con el token nuevo + fix A5-02 (regla 9) | **Pendiente de autorización** (el bot está parado: sin proceso ni lock) |
| Vídeo del flujo FGS para Play, política de privacidad alojada, keystore de subida, icono definitivo, revisión euskaldun de la ficha | Pendiente de Iker |

### Procedimiento de purga (solo con orden explícita de Iker)

```bash
# 1. Copia de seguridad fuera del repo
git clone --mirror . ../bus-dbus-backup-$(date +%Y%m%d).git
# 2. Instalar git-filter-repo (brew install git-filter-repo)
# 3. Eliminar el blob de todo el historial
git filter-repo --invert-paths --path bot-code.zip --force
# 4. Revisar: git log --all --oneline | wc -l; git rev-list --all --objects | grep zip  (debe dar vacío)
# 5. Force-push (reescribe origin/main; todos los clones deben re-clonarse)
git remote add origin <url>   # filter-repo elimina el remote
git push --force --all origin
```

Mientras no se purgue: el repo **no puede hacerse público** y el token viejo
debe estar rotado (ya lo está). Rotar invalida el token filtrado aunque el
blob siga en el historial.

## Plan por prioridad

| # | P | Hallazgo | Arreglo | Estado |
|---|---|---|---|---|
| 1 | P0 | A5-02 filtro de destino por contención | Igualdad tras normalizar (minúsculas, espacios colapsados, espacios fuera de `-`/`/`) en `Arrivals.kt` y `arrival.ts`; fixture compartido `domain-cases.json` corregido + 4 destinos reales de la L13; tests en Kotlin, core y bot | pendiente |
| 2 | P0 | A5-01 token en historial + informe falso | Rotación (hecha, arriba); reescritura de `auditoria-secretos.md` con el hallazgo y método con blobs binarios; purga gated | pendiente |
| 3 | P1 | A5-03 ficha eu inventada | Reescritura completa desde las cadenas `values-eu` reales; bloque eu de la ficha es corregido; marcada «pendiente de revisión euskaldun» | pendiente |
| 4 | P1 | A5-04 Sprint 5 «hecho» sin evidencia | SPRINTS.md y ESTADO.md: Sprint 5 «parcial» hasta cerrar A5-01/02/03/08; evidencia = informe adversarial | pendiente |
| 5 | P1 | A5-05 recuentos de tests falsos | Cifras reales tras la verificación final: 152 debug / 146 release (XML en build/test-results/) | pendiente |
| 6 | P1 | A5-06 nombres de firma inexistentes | `dbus.release.storeFile/storePassword/keyAlias/keyPassword` + `DBUS_RELEASE_*` en SPRINTS.md y ESTADO.md (copiados del README, que sí es correcto) | pendiente |
| 7 | P1 | A5-07 ficha es fuera de límites | Descripción corta ≤ 80, errata `ACCESS_FINE_LOCATION`, versión 1.1.0 (versionCode 2), nombre alineado con el APK, categoría única, vídeo FGS (guion + pendiente), tope de vigilancia y reinicio documentados, privacidad pendiente de hosting | pendiente |
| 8 | P2 | A5-08 nombre sin «no oficial» | Label del launcher «Bus Donostia (no oficial)» / «(ez-ofiziala)» (nueva clave; cabecera interna sigue «Bus Donostia»); descargo explícito en A07; crédito de tiempos sin ambigüedad | pendiente |
| 9 | P2 | A5-09 vigilancia FGS hasta 7 días | `alerts.maxWatchHours` en config.json: a las N h sin bus que coincida, el motor deja de vigilar, notifica y borra; documentado en la ficha | pendiente |
| 10 | P2 | A5-10 constantes nivel 1 inline | `noServiceStopMin`/`fetchSlackSec` → PollingConfig; `lastKnownMaxAgeSec`/`locationTimeoutSec` → GeoConfig; fuera `REFRESH_INTERVAL_MS` (muerta) y claves `recurring*`; cadenas con números → argumentos desde config | pendiente |
| 11 | P2 | A5-11 165 literales dp | Barrido explícito (ticket de auditoría) a `DesignTokens`; test guardián que cuenta literales en `ui/` | pendiente |
| 12 | P2 | A5-12 deep link sin validar | `stopId` contra catálogo (→ «Parada no encontrada», sin API ni favoritos); `highlightLine` contra las líneas de la parada (→ se ignora) | pendiente |
| 13 | P2 | A5-13 I/O en Main Thread | `filesDir` y SharedPreferences fuera del hilo principal (carga perezosa en IO; la UI espera a que los ajustes estén listos antes de pintarse) | pendiente |
| 14 | P3 | A5-14 búsqueda anima a usar códigos | «Parada o calle» y cuerpos sin «código de la marquesina» (es/eu); la coincidencia silenciosa por código se mantiene | pendiente |
| 15 | P3 | A5-15 atribución sin traducir | String propio es/eu sin nombre del endpoint para el crédito del catálogo | pendiente |
| 16 | P3 | A5-16 fallback de config indetectable | `schemaVersion` ya es obligatorio; test que parsea el asset real + marcador de origen de la config (DEFAULTS/ASSET/CACHE/REMOTE) | pendiente |
| 17 | P3 | A5-17 docs incoherentes | AGENTS.md / READMEs / ESTADO.md / SPRINTS.md alineados con HEAD (Sprint 6 hecho, residual geo cerrado, fila Sprint 6, recuentos) | pendiente |
| 18 | P3 | A5-18 `keystore.properties` sin ignorar | Añadir a `apps/android/.gitignore` | pendiente |

## Orden de ejecución

1. A5-02 en `@dbus/core` + fixture + tests (vitest) → `pnpm --filter @dbus/bot verify`.
2. A5-02 en Android + tests → `testDebugUnitTest` (prueba temprana del P0).
3. A5-01: informe de secretos corregido.
4. P1 de docs y fichas (3–7).
5. P2/P3 de código (8–16, 18) en un solo lote.
6. A5-11 (barrido dp) al final, con test guardián.
7. Verificación final: `./gradlew check assembleDebug assembleRelease -Pdbus.release.unsigned=true`
   + ambos variantes de tests con `--rerun` para el recuento real; `pnpm verify` en raíz.
8. Docs de estado con cifras reales (5, 17) y commits.

## Verificación

- `pnpm --filter @dbus/bot verify` (tsc + biome + tests; integración contra API real).
- `cd apps/android && ./gradlew check assembleDebug` + release unsigned + tests por variante.
- Regla 9: el fix del bot solo existe en producción tras reinicio con el token
  nuevo; pendiente de autorización (el bot está parado).
