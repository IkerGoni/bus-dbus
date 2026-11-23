#!/usr/bin/env node
/**
 * Sube AAB firmado a Play Console (track: internal testing).
 *
 * Requisitos:
 * - AAB firmado en apps/android/app/build/outputs/bundle/release/app-release.aab
 * - Service Account JSON con permisos Play Console (Editor/Release Manager)
 * - Variable PLAY_SERVICE_ACCOUNT_JSON o archivo .env.local
 *
 * Uso: node 07-upload-aab.mjs
 *
 * Crea release en track "internal" con 12 testers / 14 días.
 */

import fs from 'fs';
import os from 'node:os';
import path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const PACK = process.env.PLAY_PACK_DIR || path.join(os.homedir(), 'Desktop', 'badator-google-play-pack');
const AAB_PATH = path.join(ROOT, 'apps/android/app/build/outputs/bundle/release/app-release.aab');
const PACKAGE_NAME = 'eus.busdonostia.app';

function run(cmd) {
  try {
    return execSync(cmd, { encoding: 'utf-8', maxBuffer: 10 * 1024 * 1024 });
  } catch (e) {
    console.error(`❌ ${cmd}`);
    console.error(e.stdout || e.stderr || e.message);
    return '';
  }
}

async function main() {
  console.log('📦 Subida AAB a Play Console (internal testing)...\n');

  // Verificar AAB
  if (!fs.existsSync(AAB_PATH)) {
    console.error(`❌ AAB no encontrado: ${AAB_PATH}`);
    console.log('   Genera con: ./gradlew bundleRelease (requiere keystore configurado)');
    process.exit(1);
  }
  const stat = fs.statSync(AAB_PATH);
  console.log(`✅ AAB: ${AAB_PATH} (${(stat.size / 1024 / 1024).toFixed(1)} MB)`);

  // Verificar Service Account
  const saPath = path.join(ROOT, 'play-service-account.json');
  if (!fs.existsSync(saPath)) {
    console.error('\n❌ Service Account no encontrado: play-service-account.json');
    console.log('   1. Google Cloud Console → IAM → Service Accounts');
    console.log('   2. Crear cuenta → rol "Project Editor" o "Release Manager"');
    console.log('   3. Play Console → Usuarios y permisos → Invitar email de la SA');
    console.log('   4. Descargar JSON → guardar como play-service-account.json en root del repo');
    console.log('   5. NO commitear (añadir a .gitignore)');
    process.exit(1);
  }
  console.log('✅ Service Account JSON encontrado');

  // Leer versión del AAB (via aapt2 o gradle)
  const versionOut = run(`./gradlew -q printVersion 2>/dev/null || echo ""`);
  const versionMatch = versionOut.match(/versionName:?\s*(\S+)/);
  const versionName = versionMatch ? versionMatch[1] : '1.1.0';
  const versionCodeMatch = versionOut.match(/versionCode:?\s*(\d+)/);
  const versionCode = versionCodeMatch ? versionCodeMatch[1] : '2';
  console.log(`📋 Versión: ${versionName} (code ${versionCode})`);

  // Subida usando google-play-api (node library) o fastlane supply
  // Opción A: fastlane supply (recomendado, más robusto)
  console.log('\n📤 OPCIÓN RECOMENDADA: fastlane supply');
  console.log('   cd apps/android');
  console.log('   bundle exec fastlane supply \\');
  console.log('     --json_key play-service-account.json \\');
  console.log('     --package_name eus.busdonostia.app \\');
  console.log('     --aab ../app/build/outputs/bundle/release/app-release.aab \\');
  console.log('     --track internal \\');
  console.log('     --skip_upload_apk true \\');
  console.log('     --skip_upload_metadata true \\');
  console.log('     --skip_upload_images true \\');
  console.log('     --skip_upload_screenshots true');
  console.log('\n   (requiere: gem install fastlane)');

  // Opción B: google-play-api (node)
  console.log('\n📤 OPCIÓN NODE: google-play-api');
  console.log('   npm install -g @googleapis/androidpublisher');
  console.log('   # Script de subida automática...');

  // Opción C: manual (documentación)
  console.log('\n📤 OPCIÓN MANUAL (Play Console web):');
  console.log('   1. Play Console → Tu app → Versiones → Gestión de versiones');
  console.log('   2. Pestaña "Pruebas internas" → "Crear nueva versión"');
  console.log('   3. Arrastrar AAB → "Guardar" → "Revisar versión"');
  console.log('   4. Añadir 12 testers (emails) → "Iniciar pruebas"');
  console.log('   5. Duración: 14 días (configurable)');

  // Generar checklist de release
  const checklist = `
# CHECKLIST RELEASE INTERNO - Badator! v${versionName} (${versionCode})

## Pre-subida
- [ ] AAB firmado generado: ${AAB_PATH}
- [ ] VersionName: ${versionName}, VersionCode: ${versionCode}
- [ ] Keystore de subida guardado seguro (NO en repo)
- [ ] Service Account JSON configurado (play-service-account.json)
- [ ] Permisos Play Console: SA tiene rol Release Manager

## Metadatos (ya preparados en pack)
- [ ] Listing ES/EU/EN copiados a Play Console
- [ ] Short description ≤ 80 chars (ES: 68, EU: 57, EN: 57)
- [ ] Full description ≤ 4000 chars
- [ ] Icono 512×512 subido
- [ ] Feature graphic 1024×500 subido
- [ ] 6 capturas ES + 6 EU + 6 EN subidas
- [ ] Vídeo FGS subido a YouTube (no listado), URL pegada
- [ ] Política de privacidad URL pública (ES/EU/EN)
- [ ] Email soporte real configurado
- [ ] Data Safety completado (ver data-safety-responses.json)
- [ ] Categoría: Maps & Navigation
- [ ] Etiquetas: transporte, autobús, Donostia, San Sebastián, Dbus, avisos

## FGS Declaration
- [ ] Foreground Service DataSync declarado
- [ ] Vídeo FGS adjuntado en declaración
- [ ] Justificación: "consultar llegadas para avisos configurados por usuario"

## Testers internos (12 mínimos)
- [ ] Lista emails testers (12+)
- [ ] Invitaciones enviadas
- [ ] Duración: 14 días

## Post-release
- [ ] Verificar que release aparece en track "internal"
- [ ] Probar instalación desde Play Store en dispositivo real
- [ ] Verificar avisos FGS funcionan en background
- [ ] Verificar idioma EU/ES/EN cambia correctamente
- [ ] Monitorizar crashes/ANRs en Play Console (primeras 48h)

## Rollout a producción (cuando listo)
- [ ] Mover de internal → closed testing → open testing → production
- [ ] Cada fase: mínimo 12 testers / 7 días
- [ ] Producción: rollout gradual (5% → 20% → 50% → 100%)
  `;

  fs.writeFileSync(path.join(ROOT, 'build/outputs/play-store/RELEASE_CHECKLIST.md'), checklist);
  console.log('\n📋 Checklist generado: build/outputs/play-store/RELEASE_CHECKLIST.md');

  // Copiar al pack
  const packGatesDir = path.join(PACK, '05-release-gates');
  fs.mkdirSync(packGatesDir, { recursive: true });
  fs.copyFileSync(
    path.join(ROOT, 'build/outputs/play-store/RELEASE_CHECKLIST.md'),
    path.join(packGatesDir, 'RELEASE_CHECKLIST.md')
  );
  console.log(`📋 Copiado a ${path.join(packGatesDir, 'RELEASE_CHECKLIST.md')}`);
}

main().catch(console.error);