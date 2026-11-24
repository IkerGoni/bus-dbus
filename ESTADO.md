# Estado — bus-dbus

Actualizado: 2026-11-24 · Última sesión: cierre de auditorías adversariales pre-publicación (privacidad in-app, Data Safety, saneamiento Git, CI y Play Store 29/29)

## Objetivo actual

Repositorio y paquete de Google Play (`Badator!` v1.1.0) listos para publicación en repositorio remoto limpio y subida a Play Console (29/29 checks en verde).

## Hecho en esta sesión

- **Enlace a Política de Privacidad dentro de la app (`SettingsScreen.kt`):** añadido botón en Ajustes («Acerca de») que abre la política de privacidad pública según el idioma activo (`es`, `eu`, `en`), cubierto por `StringsParityTest` y `ScreenLogicTest.privacyPolicyUrlPorIdioma`. Migrados los 12 literales `.dp` de `SettingsScreen.kt` a `DesignTokens` (regla Boy Scout, límite `DesignTokensGuardTest` reducido de 165 a 153).
- **Políticas de privacidad (`01-gen-privacy.mjs` + `docs/privacy/*.html`):** corregida referencia de persistencia a `Room (SQLite) y SharedPreferences`, razón social oficial (`Compañía del Tranvía de San Sebastián, S.A.U. — Dbus`), URLs oficiales de privacidad de Dbus con HTTP 200 en ES/EU/EN, mención explícita del intent `geo:` y eliminación de frases de borrador. Escritura remota en GitHub protegida tras `--deploy`.
- **Data Safety (`02-audit-data-safety.mjs` + `data-safety-responses.json`):** `collectsLocation: false` y `location: null` (la ubicación es opcional, se procesa exclusivamente en memoria local y nunca sale del dispositivo), manteniendo `encryptsAtRest: false` y `encryptsInTransit: false`.
- **Cifras, línea temporal y documentación alineadas (`README.md`, `AGENTS.md`, `apps/android/README.md`, `auditoria-secretos.md`):** 299 tests automatizados verificados (`7` contracts + `43` core + `93` bot = `143` TS; `156` debug / `150` release en Android), línea temporal de `README.md` trazada con hashes exactos sobre el árbol publicado (`12h 42m` de `2eabe0f` a `00686f0`), enlace directo a ADR 009, `docs/.nojekyll`, `.gitignore` ampliado (`*.p12`, `*.pem`, `*.key`) y workflows con `permissions: contents: read`.
- **Saneamiento integral del historial Git:** purgados mediante `git filter-repo` correos no aprobados y rutas locales `/Users/...` en todos los blobs históricos, manteniendo el 100 % de los commits bajo la identidad `IkerGoni <8208093+IkerGoni@users.noreply.github.com>`.
- **Checklist Maestro (`08-final-checklist.mjs`):** **29/29 checks en verde (100 %)**.

## Decisiones tomadas

- Identidad legal y correo público configurados según los datos reales de la cuenta Play Console de Iker.
- Soporte para idioma inglés (EN) añadido formalmente al código y a la ficha de la tienda.
- `encryptsAtRest: false` en Data Safety para total honestidad técnica.
- Nivel 2 (tokens): congelada línea base en 165 `XX.dp` con test ratchet sin romper la regla Boy Scout en archivos ajenos.