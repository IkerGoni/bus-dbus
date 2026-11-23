# Estado — bus-dbus

Actualizado: 2026-11-23 · Última sesión: consolidación y preparación Play

## Objetivo actual

Preparar Android para publicación independiente en Google Play. Sprint 5 de publicación sigue parcial; Sprint 6 integrado. Badator! es nombre propuesto, no confirmado definitivamente.

## Hecho en la última sesión

- Cambios de remediación A5 y herramientas Play publicados en main; su corrección completa no está acreditada.
- `pnpm verify`: verde, 7 tests contracts, 43 core y 94 bot, incluyendo integración real. PWA congelada sin tests.
- Android `./gradlew check assembleDebug`: verde; tareas mayoritariamente en caché.
- APK debug copiado a `~/Desktop/Badator-debug.apk`; no es release firmada.
- El CI remoto anterior falló por lockfile pnpm desactualizado después de añadir Playwright; se actualiza el lockfile.

## Pendiente (por prioridad)

- [ ] No publicar ni usar como fuente final `docs/privacy/`, `docs/play-console/data-safety-responses.json` ni `tools/play-store/`: contienen afirmaciones no verificadas, datos de contacto/identidad supuestos, comprobaciones defectuosas y recomendaciones de traducción erróneas. Revisar también sus copias en el pack del Escritorio.
- [ ] Auditar scripts Play y CI: el checklist puede dar falsos positivos; Room no implica cifrado; el AAB anterior era unsigned; no confundir pruebas internas con requisitos de pruebas cerradas. No asumir que la app dispone de interfaz inglesa.
- [ ] Purga del historial autorizado pendiente: `bot-code.zip` contiene token anterior; evaluar refs, clones y respaldo antes de reescribir y coordinar force push. Token rotado según Iker.
- [ ] Reinicio del bot autorizado pendiente tras cambio en bot/shared; comprobar instancia, despliegue y lock sin exponer credenciales.
- [ ] Contrastar A5-09, A5-11, A5-12 y A5-13 con código y tests; no declarar remediación completa sin evidencia.
- [ ] Política de privacidad, Data Safety, firma AAB, identidad de soporte, marca, capturas reales, FGS y prueba en dispositivo real pendientes de verificación y decisión.
- [ ] Revisar CI remoto posterior a actualización de lockfile; integración real es no bloqueante en workflow actual y puede ocultar fallos.
- [ ] Probar avisos con Doze y ubicación aproximada en móvil real; valorar sonido de alarma propuesto.

## Decisiones tomadas

- ADR 009: Android nativo; PWA congelada, no modificar sin acuerdo.
- Datos de llegadas reales y API Dbus en HTTP; sin simular resultados de producción.
- Filtro de destino por igualdad tras normalizar; maxWatchHours=3 para vigilancia sin llegada coincidente.
- Publicación independiente, sin contratar abogado ni auditor externo; no inventar hechos o aprobaciones.

## Esperando a Iker

- Confirmar identidad y correo de soporte, marca definitiva y decisiones de publicación.
- Resolver el worktree ajeno del pool Kilo si sigue en uso; no eliminarlo sin comprobar propietario y actividad.

## Git

Main publicado hasta el cierre; consultar `git status -sb` y `git worktree list` para estado exacto. Archivos temporales Kanban sin seguimiento se conservan por seguridad.

## Siguiente sesión

- Leer `AGENTS.md`, este archivo, `docs/auditoria/2026-11-sprint5-remediacion.md` y `docs/07-android-mvp/auditoria-secretos.md`.
- Empezar por revisión de seguridad y corrección de las falsas afirmaciones del toolkit Play; no ejecutar scripts que publiquen o sobrescriban sin inspección.
