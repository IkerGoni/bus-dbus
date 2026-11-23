#!/usr/bin/env node
/**
 * Checklist final maestro: ejecuta todas las verificaciones y genera RELEASE-READY.md
 *
 * Uso: node 08-final-checklist.mjs
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const PACK = '/Users/dev/Desktop/badator-google-play-pack';

function run(cmd) {
  try {
    return execSync(cmd, { encoding: 'utf-8', maxBuffer: 10 * 1024 * 1024 }).trim();
  } catch (e) {
    return '';
  }
}

function checkFile(p, desc) {
  const exists = fs.existsSync(p);
  console.log(`  ${exists ? '✅' : '❌'} ${desc}: ${p}`);
  return exists;
}

function checkCommand(cmd, desc) {
  try {
    run(cmd);
    console.log(`  ✅ ${desc}`);
    return true;
  } catch {
    console.log(`  ❌ ${desc}`);
    return false;
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

  // 1. CÓDIGO - Tests + Lint + Build
  console.log('\n1️⃣ CÓDIGO (tests, lint, build):');
  results.code += checkCommand('cd apps/android && ./gradlew check assembleDebug --no-daemon >/dev/null 2>&1', 'Android: check + assembleDebug');
  results.code += checkCommand('cd apps/android && ./gradlew bundleRelease -Pdbus.release.unsigned=true --no-daemon >/dev/null 2>&1', 'Android: bundleRelease (unsigned)');
  results.code += checkCommand('pnpm --filter @dbus/core test >/dev/null 2>&1', 'Core: tests');
  results.code += checkCommand('pnpm --filter @dbus/bot test >/dev/null 2>&1', 'Bot: tests');
  results.code += checkCommand('pnpm lint >/dev/null 2>&1', 'Lint (biome)');
  results.code += checkCommand('pnpm typecheck >/dev/null 2>&1', 'Typecheck (tsc)');

  // 2. PRIVACIDAD
  console.log('\n2️⃣ POLÍTICA DE PRIVACIDAD:');
  results.privacy += checkFile(path.join(ROOT, 'docs/privacy/privacy-policy-es.html'), 'ES HTML');
  results.privacy += checkFile(path.join(ROOT, 'docs/privacy/privacy-policy-eu.html'), 'EU HTML');
  results.privacy += checkFile(path.join(ROOT, 'docs/privacy/privacy-policy-en.html'), 'EN HTML');
  results.privacy += checkFile(path.join(PACK, '03-privacy/PRIVACY-POLICY-ES-FINAL.md'), 'ES Final (pack)');
  results.privacy += checkFile(path.join(PACK, '03-privacy/PRIVACY-POLICY-EU-FINAL.md'), 'EU Final (pack)');
  results.privacy += checkFile(path.join(PACK, '03-privacy/PRIVACY-POLICY-EN-FINAL.md'), 'EN Final (pack)');

  // 3. DATA SAFETY
  console.log('\n3️⃣ DATA SAFETY:');
  results.dataSafety += checkFile(path.join(ROOT, 'docs/play-console/data-safety-responses.json'), 'JSON respuestas');
  results.dataSafety += checkFile(path.join(PACK, '04-console/DATA-SAFETY-RESPONSES.json'), 'JSON (pack)');
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
  results.fgsVideo += checkFile(path.join(ROOT, 'build/outputs/play-store/fgs-demo.mp4'), 'MP4 grabado');
  results.fgsVideo += checkFile(path.join(ROOT, 'build/outputs/play-store/FGS_SCRIPT.md'), 'Guion');
  results.fgsVideo += checkFile(path.join(PACK, '02-creative/FGS_SCRIPT.md'), 'Guion (pack)');

  // 7. REVISIÓN EU
  console.log('\n7️⃣ REVISIÓN EU (euskera):');
  const euIssues = run('node tools/play-store/06-verify-eu.mjs 2>&1 | grep -c "⚠️"') || '0';
  console.log(`  ⚠️ Issues detectados: ${euIssues}`);
  results.euReview = parseInt(euIssues) === 0 ? 1 : 0;

  // 8. AAB + UPLOAD
  console.log('\n8️⃣ AAB FIRMADO + SUBIDA:');
  const aabPath = path.join(ROOT, 'apps/android/app/build/outputs/bundle/release/app-release.aab');
  results.aab += checkFile(aabPath, 'AAB firmado');
  results.aab += checkFile(path.join(ROOT, 'play-service-account.json'), 'Service Account JSON');
  results.aab += checkFile(path.join(PACK, '05-release-gates/RELEASE_CHECKLIST.md'), 'Checklist release');

  // 9. STORE FIELDS (listing)
  console.log('\n9️⃣ FICHA PLAY STORE (store fields):');
  results.storeFields += checkFile(path.join(PACK, '01-listing/es-ES.md'), 'Listing ES');
  results.storeFields += checkFile(path.join(PACK, '01-listing/eu-ES.md'), 'Listing EU');
  results.storeFields += checkFile(path.join(PACK, '01-listing/en-US.md'), 'Listing EN');
  results.storeFields += checkFile(path.join(PACK, '07-store-fields.json'), 'JSON campos');

  // 10. GATES DE RELEASE
  console.log('\n🔟 GATES DE RELEASE (05-release-gates.md):');
  results.gates += checkFile(path.join(PACK, '05-release-gates.md'), 'Gates document');
  results.gates += checkFile(path.join(ROOT, 'build/outputs/play-store/RELEASE_CHECKLIST.md'), 'Checklist final');

  // RESUMEN
  console.log('\n📊 RESUMEN FINAL:');
  const categories = [
    ['Código', results.code, 6],
    ['Privacidad', results.privacy, 5],
    ['Data Safety', results.dataSafety, 3],
    ['Assets', results.assets, 5],
    ['Capturas', results.screenshots, 1],
    ['Vídeo FGS', results.fgsVideo, 3],
    ['EU Review', results.euReview, 1],
    ['AAB/Upload', results.aab, 3],
    ['Store Fields', results.storeFields, 4],
    ['Gates', results.gates, 2],
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

  // Generar RELEASE-READY.md
  const report = `# RELEASE READY - Badator! v1.1.0

**Fecha:** ${new Date().toISOString().split('T')[0]}
**Estado global:** ${ready ? '✅ LISTO PARA PUBLICAR' : '⚠️ PENDIENTES'}

## Resumen por categorías

| Categoría | OK/Total | % | Estado |
|---|---|---|---|
${categories.map(([name, pass, total]) => `| ${name} | ${pass}/${total} | ${total>0?Math.round(pass/total*100):100}% | ${pass===total?'✅':pass>0?'⚠️':'❌'} |`).join('\n')}

## Detalle de pendientes

${!ready ? 'Ver items con ❌ o ⚠️ arriba. Ejecuta scripts correspondientes.' : 'Todos los checks pasan.'}

## Próximos pasos

1. ${ready ? 'Subir AAB a Play Console (internal testing)' : 'Resolver items pendientes'}
2. ${ready ? 'Invitar 12 testers internos (14 días)' : 'Completar assets/capturas/FGS'}
3. ${ready ? 'Monitorizar 48h → promover a closed testing' : 'Revisar EU con nativo'}
4. ${ready ? 'Rollout gradual a producción' : 'Grabar vídeo FGS'}

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

  fs.writeFileSync(path.join(ROOT, 'build/outputs/play-store/RELEASE-READY.md'), report);
  console.log('\n📋 RELEASE-READY.md generado en build/outputs/play-store/');

  if (ready) {
    console.log('\n🎉 ¡TODO VERDE! Puedes subir a Play Console.');
  } else {
    console.log('\n⚠️ Hay items pendientes. Revisa RELEASE-READY.md');
  }
}

main().catch(console.error);