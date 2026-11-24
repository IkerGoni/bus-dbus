#!/usr/bin/env node
/**
 * Checklist final maestro: ejecuta todas las verificaciones y genera RELEASE-READY.md
 *
 * Uso: node 08-final-checklist.mjs
 */

import fs from 'fs';
import os from 'node:os';
import path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const PACK = process.env.PLAY_PACK_DIR || path.join(os.homedir(), 'Desktop', 'badator-google-play-pack');

function resolveEnvPath(envVar, fallbackPath) {
  if (process.env[envVar]) return process.env[envVar];
  if (fallbackPath && fs.existsSync(fallbackPath)) return fallbackPath;
  return undefined;
}

function run(cmd, env = {}) {
  const javaHome = resolveEnvPath('JAVA_HOME', '/opt/homebrew/opt/openjdk@17');
  const androidHome = resolveEnvPath('ANDROID_HOME', '/opt/homebrew/share/android-commandlinetools');
  const mergedEnv = { ...process.env };
  if (javaHome) {
    mergedEnv.JAVA_HOME = javaHome;
    mergedEnv.PATH = `${path.join(javaHome, 'bin')}${path.delimiter}${mergedEnv.PATH || ''}`;
  }
  if (androidHome) {
    mergedEnv.ANDROID_HOME = androidHome;
  }

  return execSync(cmd, {
    encoding: 'utf-8',
    maxBuffer: 10 * 1024 * 1024,
    env: {
      ...mergedEnv,
      ...env,
    },
  }).trim();
}

function checkFile(p, desc, minSize = 0) {
  const exists = fs.existsSync(p);
  let sizeOk = true;
  if (exists && minSize > 0) {
    const stats = fs.statSync(p);
    sizeOk = stats.size >= minSize;
  }
  const pass = exists && sizeOk;
  console.log(`  ${pass ? '✅' : '❌'} ${desc}: ${p}${exists && minSize > 0 ? ` (${(fs.statSync(p).size / 1024).toFixed(1)} KB)` : ''}`);
  return pass ? 1 : 0;
}

function checkPrivacyFile(p, desc) {
  if (!fs.existsSync(p)) {
    console.log(`  ❌ ${desc}: no existe ${p}`);
    return 0;
  }
  const content = fs.readFileSync(p, 'utf-8');
  const badPatterns = [
    '[FECHA_REAL]',
    '[BENETAKO_DATA]',
    '[EMAIL_SOPORTE_REAL]',
    'Dev iGoni',
    '⚠️',
    'BORRADOR',
    'ZIRRIBORROA',
    'Pendiente',
    'PENDIENTE',
    'Verificar',
    'EZ ARGITARATU',
    'VERSIÓN FINAL',
    'BERRIZKO BERTZIOA',
    'TODO',
    'FIXME',
    '完全',
  ];
  const foundBad = badPatterns.filter(b => content.includes(b));
  if (foundBad.length > 0) {
    console.log(`  ❌ ${desc}: contiene placeholders (${foundBad.join(', ')})`);
    return 0;
  }
  console.log(`  ✅ ${desc}: limpio y verificado`);
  return 1;
}

function checkDataSafetyJson(p, desc) {
  if (!fs.existsSync(p)) {
    console.log(`  ❌ ${desc}: no existe ${p}`);
    return 0;
  }
  try {
    const json = JSON.parse(fs.readFileSync(p, 'utf-8'));
    if (json.securityPractices?.encryptsAtRest !== false) {
      console.log(`  ❌ ${desc}: encryptsAtRest debe ser false`);
      return 0;
    }
    if (json.securityPractices?.encryptsInTransit !== false) {
      console.log(`  ❌ ${desc}: encryptsInTransit debe ser false`);
      return 0;
    }
    if (json.dataCollection?.collectsLocation !== false || json.dataTypes?.location !== null) {
      console.log(`  ❌ ${desc}: collectsLocation debe ser false y dataTypes.location null (procesamiento 100% en dispositivo)`);
      return 0;
    }
    if (json.dataSharing?.sharesLocation !== false) {
      console.log(`  ❌ ${desc}: sharesLocation debe ser false`);
      return 0;
    }
    const settingsKt = fs.readFileSync(
      path.join(ROOT, 'apps/android/app/src/main/kotlin/eus/busdonostia/app/ui/settings/SettingsScreen.kt'),
      'utf-8',
    );
    if (!settingsKt.includes('privacyPolicyIntent')) {
      console.log(`  ❌ ${desc}: SettingsScreen.kt no enlaza a privacyPolicyIntent`);
      return 0;
    }
    console.log(`  ✅ ${desc}: verificado (collectsLocation=false, encryptsAtRest=false, cleartext=true, in-app privacy link=true)`);
    return 1;
  } catch (e) {
    console.log(`  ❌ ${desc}: JSON inválido`);
    return 0;
  }
}

function checkCommand(cmd, desc) {
  try {
    run(cmd);
    console.log(`  ✅ ${desc}`);
    return 1;
  } catch (err) {
    console.log(`  ❌ ${desc}`);
    const msg = (err.stderr || err.stdout || err.message || '').toString().trim().split('\n').filter(Boolean).slice(-2).join(' ');
    if (msg) console.log(`     Detalle: ${msg}`);
    return 0;
  }
}

function checkAabSigned(aabPath) {
  if (!fs.existsSync(aabPath)) {
    console.log(`  ❌ AAB (release): no existe ${aabPath}`);
    return 0;
  }
  const stats = fs.statSync(aabPath);
  const sizeKb = (stats.size / 1024).toFixed(1);
  if (stats.size < 1024 * 1024) {
    console.log(`  ❌ AAB (release): demasiado pequeño (${sizeKb} KB): ${aabPath}`);
    return 0;
  }

  try {
    const out = run(`jarsigner -verify "${aabPath}"`);
    if (/jar is unsigned/i.test(out)) {
      console.log(`  ❌ AAB (release) SIN FIRMAR (unsigned, ${sizeKb} KB): ${aabPath}`);
      return 0;
    }
    if (/jar verified\./i.test(out)) {
      console.log(`  ✅ AAB (release) firmado y verificado (${sizeKb} KB): ${aabPath}`);
      return 1;
    }
    console.log(`  ❌ AAB (release) verificación de firma inconcluyente (${sizeKb} KB): ${out.split('\n')[0]}`);
    return 0;
  } catch (err) {
    const errText = `${err.stderr || ''} ${err.stdout || ''} ${err.message || ''}`;
    if (/jar is unsigned/i.test(errText)) {
      console.log(`  ❌ AAB (release) SIN FIRMAR (unsigned, ${sizeKb} KB): ${aabPath}`);
      return 0;
    }
    if (!/not found|ENOENT|no se reconoce/i.test(errText)) {
      console.log(`  ❌ AAB (release) firma inválida (${sizeKb} KB): ${errText.trim().split('\n')[0]}`);
      return 0;
    }
  }

  try {
    const entries = run(`unzip -l "${aabPath}"`);
    const hasSf = /META-INF\/[^/\s]+\.SF\b/i.test(entries);
    const hasCert = /META-INF\/[^/\s]+\.(RSA|EC|DSA)\b/i.test(entries);
    if (hasSf && hasCert) {
      console.log(`  ✅ AAB (release) firmado (META-INF verificado, ${sizeKb} KB): ${aabPath}`);
      return 1;
    }
    console.log(`  ❌ AAB (release) SIN FIRMAR (sin entradas de firma en META-INF, ${sizeKb} KB): ${aabPath}`);
    return 0;
  } catch (err) {
    console.log(`  ❌ AAB (release): no se pudo verificar firma (${err.message})`);
    return 0;
  }
}

async function main() {
  console.log('🏁 CHECKLIST FINAL - Badator! Play Store Release\n');

  const results = {
    code: 0,
    privacy: 0,
    dataSafety: 0,
    assets: 0,
    screenshots: 0,
    fgsVideo: 0,
    euReview: 0,
    aab: 0,
    storeFields: 0,
    gates: 0,
  };

  // 1. CÓDIGO (Android + Shared) - Tests + Lint + Build
  console.log('\n1️⃣ CÓDIGO (Android + Shared packages):');
  results.code += checkCommand('cd apps/android && ./gradlew check assembleDebug --no-daemon', 'Android: check + assembleDebug (tests JVM + lint)');
  results.code += checkCommand('pnpm --filter @dbus/contracts test', 'Contracts: tests');
  results.code += checkCommand('pnpm --filter @dbus/core test', 'Core: tests');
  results.code += checkCommand('npx biome check packages', 'Lint packages (biome)');
  results.code += checkCommand('npx tsc -b packages/contracts packages/core', 'Typecheck packages (tsc)');

  // 2. PRIVACIDAD
  console.log('\n2️⃣ POLÍTICA DE PRIVACIDAD:');
  results.privacy += checkPrivacyFile(path.join(ROOT, 'docs/privacy/privacy-policy-es.html'), 'ES HTML');
  results.privacy += checkPrivacyFile(path.join(ROOT, 'docs/privacy/privacy-policy-eu.html'), 'EU HTML');
  results.privacy += checkPrivacyFile(path.join(ROOT, 'docs/privacy/privacy-policy-en.html'), 'EN HTML');
  results.privacy += checkPrivacyFile(path.join(PACK, '03-privacy/PRIVACY-POLICY-ES-FINAL.md'), 'ES Final (pack)');
  results.privacy += checkPrivacyFile(path.join(PACK, '03-privacy/PRIVACY-POLICY-EU-FINAL.md'), 'EU Final (pack)');
  results.privacy += checkPrivacyFile(path.join(PACK, '03-privacy/PRIVACY-POLICY-EN-FINAL.md'), 'EN Final (pack)');

  // 3. DATA SAFETY
  console.log('\n3️⃣ DATA SAFETY:');
  results.dataSafety += checkDataSafetyJson(path.join(ROOT, 'docs/play-console/data-safety-responses.json'), 'JSON respuestas');
  results.dataSafety += checkDataSafetyJson(path.join(PACK, '04-console/DATA-SAFETY-RESPONSES.json'), 'JSON (pack)');
  results.dataSafety += checkFile(path.join(PACK, '04-console/DATA-SAFETY-AUDIT.md'), 'Auditoría');

  // 4. ASSETS GRÁFICOS
  console.log('\n4️⃣ ASSETS GRÁFICOS:');
  results.assets += checkFile(path.join(ROOT, 'build/outputs/play-store/icon-512.png'), 'Icono 512×512');
  results.assets += checkFile(path.join(ROOT, 'build/outputs/play-store/feature-graphic-1024x500.png'), 'Feature graphic 1024×500');
  results.assets += checkFile(path.join(PACK, '02-creative/icon-512.png'), 'Icono (pack)');
  results.assets += checkFile(path.join(PACK, '02-creative/feature-graphic-1024x500.png'), 'Feature graphic (pack)');
  results.assets += checkFile(path.join(PACK, '02-creative/ART-DIRECTION.md'), 'Art direction');

  // 5. CAPTURAS
  console.log('\n5️⃣ CAPTURAS DE PANTALLA (18 = 6×3 idiomas):');
  const screensDir = path.join(ROOT, 'build/outputs/play-store/screenshots');
  if (fs.existsSync(screensDir)) {
    const screens = fs.readdirSync(screensDir).filter(f => f.endsWith('.png'));
    console.log(`  📸 ${screens.length}/18 capturas en ${screensDir}`);
    results.screenshots = screens.length >= 18 ? 1 : 0;
  } else {
    console.log(`  ❌ Directorio no existe: ${screensDir}`);
  }
  const packScreens = path.join(PACK, '02-creative/screenshots');
  if (fs.existsSync(packScreens)) {
    const packFiles = fs.readdirSync(packScreens).filter(f => f.endsWith('.png'));
    console.log(`  📋 ${packFiles.length}/18 en pack`);
  }

  // 6. VÍDEO FGS
  console.log('\n6️⃣ VÍDEO FGS:');
  results.fgsVideo += checkFile(path.join(ROOT, 'build/outputs/play-store/fgs-demo.mp4'), 'MP4 grabado', 100 * 1024);
  results.fgsVideo += checkFile(path.join(PACK, '04-console/FGS-REVIEW-TEMPLATE.md'), 'Guion / Plantilla FGS (pack)');

  // 7. REVISIÓN EU
  console.log('\n7️⃣ REVISIÓN EU (euskera):');
  let euIssues = 0;
  let euFailed = false;
  try {
    const euOutput = run('node tools/play-store/06-verify-eu.mjs');
    euIssues = (euOutput.match(/⚠️/g) || []).length;
  } catch (err) {
    euFailed = true;
    const msg = (err.stderr || err.stdout || err.message || '').toString().trim();
    console.log(`  ❌ Error ejecutando 06-verify-eu.mjs: ${msg.split('\n').filter(Boolean).slice(-3).join(' ')}`);
  }
  if (euFailed) {
    results.euReview = 0;
  } else {
    console.log(`  ${euIssues === 0 ? '✅' : '⚠️'} Issues detectados: ${euIssues}`);
    results.euReview = euIssues === 0 ? 1 : 0;
  }

  // 8. AAB + UPLOAD
  console.log('\n8️⃣ AAB FIRMADO + SUBIDA:');
  const aabPath = path.join(ROOT, 'apps/android/app/build/outputs/bundle/release/app-release.aab');
  results.aab += checkAabSigned(aabPath);
  const saPath = path.join(ROOT, 'play-service-account.json');
  if (fs.existsSync(saPath)) {
    console.log(`  ✅ Service Account JSON (opcional CLI): ${saPath}`);
  } else {
    console.log('  ℹ️ Service Account JSON (opcional CLI): no presente (subida manual en Play Console disponible)');
  }

  // 9. STORE FIELDS (listing)
  console.log('\n9️⃣ FICHA PLAY STORE (store fields):');
  results.storeFields += checkFile(path.join(PACK, '01-listing/es-ES.md'), 'Listing ES');
  results.storeFields += checkFile(path.join(PACK, '01-listing/eu-ES.md'), 'Listing EU');
  results.storeFields += checkFile(path.join(PACK, '01-listing/en-US.md'), 'Listing EN');
  results.storeFields += checkFile(path.join(PACK, '07-store-fields.json'), 'JSON campos');

  // 10. GATES DE RELEASE
  console.log('\n🔟 GATES DE RELEASE (05-release-gates.md):');
  results.gates += checkFile(path.join(PACK, '05-release-gates.md'), 'Gates document (pack)');

  // RESUMEN
  console.log('\n📊 RESUMEN FINAL:');
  const categories = [
    ['Código', results.code, 5],
    ['Privacidad', results.privacy, 6],
    ['Data Safety', results.dataSafety, 3],
    ['Assets', results.assets, 5],
    ['Capturas', results.screenshots, 1],
    ['Vídeo FGS', results.fgsVideo, 2],
    ['EU Review', results.euReview, 1],
    ['AAB/Upload', results.aab, 1],
    ['Store Fields', results.storeFields, 4],
    ['Gates', results.gates, 1],
  ];

  let totalPass = 0, totalChecks = 0;
  for (const [name, pass, total] of categories) {
    const pct = total > 0 ? Math.round((pass / total) * 100) : 100;
    const status = pass === total ? '✅' : (pass > 0 ? '⚠️' : '❌');
    console.log(`  ${status} ${name}: ${pass}/${total} (${pct}%)`);
    totalPass += pass;
    totalChecks += total;
  }

  const overall = totalChecks > 0 ? Math.round((totalPass / totalChecks) * 100) : 0;
  const ready = overall === 100;

  console.log(`\n🎯 TOTAL: ${totalPass}/${totalChecks} (${overall}%) ${ready ? '🎉 LISTO PARA PUBLICAR' : '⚠️ PENDIENTES'}`);

  const remediationMap = {
    'Código': 'Corregir tests, lint, typecheck o compilación en Android / packages compartidos (`./gradlew check assembleDebug`, `pnpm verify`)',
    'Privacidad': 'Completar y verificar las políticas de privacidad ES/EU/EN sin placeholders (`node tools/play-store/01-gen-privacy.mjs`)',
    'Data Safety': 'Generar y verificar el formulario Data Safety (`node tools/play-store/02-audit-data-safety.mjs`)',
    'Assets': 'Generar icono 512×512 y feature graphic 1024×500 (`node tools/play-store/03-gen-assets.mjs`)',
    'Capturas': 'Capturar las 18 pantallas ES/EU/EN desde emulador (`node tools/play-store/04-capture-screens.mjs`)',
    'Vídeo FGS': 'Grabar `fgs-demo.mp4` y completar la plantilla FGS (`node tools/play-store/05-record-fgs.mjs`)',
    'EU Review': 'Revisar y corregir los literalismos/inconsistencias en euskera (`node tools/play-store/06-verify-eu.mjs`)',
    'AAB/Upload': 'Generar y firmar `app-release.aab` con el keystore de release (`./gradlew bundleRelease`)',
    'Store Fields': 'Completar los listings ES/EU/EN y `07-store-fields.json` en el pack de Play Store',
    'Gates': 'Completar `05-release-gates.md` en el pack de Play Store',
  };

  const failedCategories = categories.filter(([, pass, total]) => pass < total);
  const nextSteps = ready
    ? [
        'Subir AAB firmado a Play Console (internal testing)',
        'Invitar 12 testers internos (14 días)',
        'Monitorizar 48h → promover a closed testing',
        'Rollout gradual a producción',
      ]
    : failedCategories.map(([name, pass, total]) => `**${name}** (${pass}/${total}): ${remediationMap[name]}`);

  // Generar RELEASE-READY.md
  const report = `# RELEASE READY - Badator! v1.1.0

**Fecha:** ${new Date().toISOString().split('T')[0]}
**Estado global:** ${ready ? '✅ LISTO PARA PUBLICAR' : '⚠️ PENDIENTES'}

## Resumen por categorías

| Categoría | OK/Total | % | Estado |
|---|---|---|---|
${categories.map(([name, pass, total]) => `| ${name} | ${pass}/${total} | ${total>0?Math.round(pass/total*100):100}% | ${pass===total?'✅':pass>0?'⚠️':'❌'} |`).join('\n')}

## Detalle de pendientes

${!ready ? failedCategories.map(([name, pass, total]) => `- ${pass > 0 ? '⚠️' : '❌'} **${name}**: ${pass}/${total} checks superados`).join('\n') : 'Todos los checks pasan.'}

## Próximos pasos

${nextSteps.map((step, i) => `${i + 1}. ${step}`).join('\n')}

## Archivos generados

- \`docs/privacy/privacy-policy-{es,eu,en}.html\` → URLs GitHub Pages
- \`docs/play-console/data-safety-responses.json\` → Play Console
- \`build/outputs/play-store/icon-512.png\` + \`feature-graphic-1024x500.png\`
- \`build/outputs/play-store/screenshots/\` (18 PNGs)
- \`build/outputs/play-store/fgs-demo.mp4\` + \`FGS_SCRIPT.md\`
- \`build/outputs/play-store/RELEASE_CHECKLIST.md\`
- \`apps/android/app/build/outputs/bundle/release/app-release.aab\` (firmado)

---

**Firma:** _________________________ **Fecha:** _______________
`;

  fs.mkdirSync(path.join(ROOT, 'build/outputs/play-store'), { recursive: true });
  fs.writeFileSync(path.join(ROOT, 'build/outputs/play-store/RELEASE-READY.md'), report);
  console.log('\n📋 RELEASE-READY.md generado en build/outputs/play-store/');

  if (ready) {
    console.log('\n🎉 ¡TODO VERDE! Puedes subir a Play Console.');
  } else {
    console.log('\n⚠️ Hay items pendientes. Revisa RELEASE-READY.md');
    process.exitCode = 1;
  }
}

main().catch(err => {
  console.error(err);
  process.exitCode = 1;
});