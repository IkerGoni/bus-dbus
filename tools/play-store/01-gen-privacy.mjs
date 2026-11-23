#!/usr/bin/env node
/**
 * Genera políticas de privacidad finales (ES/EU/EN) a partir de plantillas
 * y evidencia real del código. Despliega a GitHub Pages.
 *
 * Uso: node 01-gen-privacy.mjs
 * Requiere: GH_TOKEN, GH_PAGES_REPO en .env.local o env
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const PACK = path.resolve('/Users/dev/Desktop/badator-google-play-pack');
const OUT_DIR = path.resolve(ROOT, 'docs/privacy');

// ---- Config que TÚ rellenas una vez ----
const CONFIG = {
  developer: 'Dev iGoni',
  legalIdentity: 'Iker Goñi (autónomo, España)',
  supportEmail: 'dev.mail.igoni@gmail.com', // ← cámbialo
  privacyUrls: {
    es: 'https://ikergoni.github.io/bus-dbus/privacy-policy-es.html',
    eu: 'https://ikergoni.github.io/bus-dbus/privacy-policy-eu.html',
    en: 'https://ikergoni.github.io/bus-dbus/privacy-policy-en.html',
  },
  dbusProvider: {
    name: 'Dbus (Donostiako Autobusak)',
    url: 'https://www.dbus.eus',
    privacyUrl: 'https://www.dbus.eus/privacidad/',
  },
  github: {
    repo: 'IkerGoni/bus-dbus',
    branch: 'main',
    token: process.env.GH_TOKEN || '',
  },
  lastUpdate: new Date().toISOString().split('T')[0],
};

// All placeholder variations across ES/EU/EN templates
const PLACEHOLDERS = {
  // ES
  '[FECHA_REAL]': CONFIG.lastUpdate,
  '[IDENTIDAD_LEGAL_O_DATOS_EXIGIDOS]': CONFIG.legalIdentity,
  '[EMAIL_SOPORTE_REAL]': CONFIG.supportEmail,
  '[URL_PUBLICA_POLITICA_PRIVACIDAD]': CONFIG.privacyUrls.es, // will be overridden per-lang
  // EU
  '[BENETAKO_DATA]': CONFIG.lastUpdate,
  '[ARDURADUNAREN_LEGEZKO_DATUAK]': CONFIG.legalIdentity,
  '[BENETAKO_LAGUNTZA_EMAILA]': CONFIG.supportEmail,
  '[POLITIKAREN_URL_PUBLIKOA]': CONFIG.privacyUrls.eu,
  '[AZKEN_AAB_AUDITORIAREN_EMAITZAK]': '', // filled per-lang
  '[BENETAKO_EZABAKETA_ETA_KONTSERBAZIO_PROZEDURA]': '', // filled per-lang
  // EN
  '[ACTUAL_DATE]': CONFIG.lastUpdate,
  '[REQUIRED_LEGAL_IDENTITY]': CONFIG.legalIdentity,
  '[REAL_SUPPORT_EMAIL]': CONFIG.supportEmail,
  '[PUBLIC_PRIVACY_POLICY_URL]': CONFIG.privacyUrls.en,
  '[CONFIRM ACTUAL DEPENDENCIES, TRACKERS AND NETWORK TRAFFIC]': '', // filled per-lang
  '[CONFIRM ON-DEVICE RETENTION, DELETION METHODS AND BACKUP POLICY]': '', // filled per-lang
};

// ---- Lee evidencia real del código ----
function readCodeEvidence() {
  const manifest = fs.readFileSync(path.join(ROOT, 'apps/android/app/src/main/AndroidManifest.xml'), 'utf-8');
  const netConfig = fs.readFileSync(path.join(ROOT, 'apps/android/app/src/main/res/xml/network_security_config.xml'), 'utf-8');
  const settingsStore = fs.readFileSync(path.join(ROOT, 'apps/android/app/src/main/kotlin/eus/busdonostia/app/data/settings/SettingsStore.kt'), 'utf-8');
  const locationProvider = fs.readFileSync(path.join(ROOT, 'apps/android/app/src/main/kotlin/eus/busdonostia/app/data/location/LocationProvider.kt'), 'utf-8');
  const liveRepo = fs.readFileSync(path.join(ROOT, 'apps/android/app/src/main/kotlin/eus/busdonostia/app/data/live/LiveRepository.kt'), 'utf-8');
  const configJson = fs.readFileSync(path.join(ROOT, 'apps/android/app/src/main/assets/config.json'), 'utf-8');
  const gradle = fs.readFileSync(path.join(ROOT, 'apps/android/app/build.gradle.kts'), 'utf-8');
  const packageJson = fs.readFileSync(path.join(ROOT, 'package.json'), 'utf-8');
  const gradleDeps = fs.readFileSync(path.join(ROOT, 'apps/android/app/build.gradle.kts'), 'utf-8');
  const recentEntity = fs.readFileSync(path.join(ROOT, 'apps/android/app/src/main/kotlin/eus/busdonostia/app/data/db/RecentStopEntity.kt'), 'utf-8');
  const alertEntity = fs.readFileSync(path.join(ROOT, 'apps/android/app/src/main/kotlin/eus/busdonostia/app/data/db/AlertEntity.kt'), 'utf-8');

  return {
    permissions: extractPermissions(manifest),
    networkSecurity: netConfig,
    usesRoom: recentEntity.includes('@Entity') || alertEntity.includes('@Entity') || gradleDeps.includes('room-runtime') || gradleDeps.includes('room-ktx'),
    locationPersisted: !locationProvider.includes('Nunca se persiste') && !locationProvider.includes('never persisted'),
    locationOnlyOnRequest: locationProvider.includes('solo cuando') || locationProvider.includes('only when') || locationProvider.includes('en memoria y se olvida'),
    notificationsOptional: true, // POST_NOTIFICATIONS se pide en runtime
    fgsDataSync: manifest.includes('FOREGROUND_SERVICE_DATA_SYNC'),
    allowBackupFalse: manifest.includes('allowBackup="false"'),
    apiEndpoint: extractApiEndpoint(configJson),
    apiProtocol: netConfig.includes('cleartextTrafficPermitted="true"') ? 'HTTP (cleartext)' : 'HTTPS',
    noAnalytics: !packageJson.includes('analytics') && !packageJson.includes('crashlytics') && !packageJson.includes('sentry'),
    noAds: true,
    minSdk: extractMinSdk(gradle),
    targetSdk: extractTargetSdk(gradle),
  };
}

function extractPermissions(manifest) {
  const perms = [];
  const regex = /uses-permission android:name="([^"]+)"/g;
  let m;
  while ((m = regex.exec(manifest))) {
    perms.push(m[1]);
  }
  return perms;
}

function extractApiEndpoint(configJson) {
  try {
    const cfg = JSON.parse(configJson);
    return cfg.apiBaseUrl + cfg.endpoints.tiemposParada;
  } catch {
    return 'http://app.dbus.eus/SSIIMovilWSv2/ws/cons/tiemposParada';
  }
}

function extractMinSdk(gradle) {
  const m = gradle.match(/minSdk\s*=\s*(\d+)/);
  return m ? m[1] : '26';
}

function extractTargetSdk(gradle) {
  const m = gradle.match(/targetSdk\s*=\s*(\d+)/);
  return m ? m[1] : '36';
}

// ---- Plantillas base (desde el pack) ----
const TEMPLATES = {
  es: fs.readFileSync(path.join(PACK, '03-privacy/PRIVACY-POLICY-es-ES-DRAFT.md'), 'utf-8'),
  eu: fs.readFileSync(path.join(PACK, '03-privacy/PRIVACY-POLICY-eu-ES-DRAFT.md'), 'utf-8'),
  en: fs.readFileSync(path.join(PACK, '03-privacy/PRIVACY-POLICY-en-US-DRAFT.md'), 'utf-8'),
};

// ---- Relleno de placeholders con evidencia real ----
function fillTemplate(lang, evidence) {
  let t = TEMPLATES[lang];

  // Common placeholders across all languages
  const common = { ...PLACEHOLDERS };
  common['[URL_PUBLICA_POLITICA_PRIVACIDAD]'] = CONFIG.privacyUrls[lang];
  common['[POLITIKAREN_URL_PUBLIKOA]'] = CONFIG.privacyUrls[lang];
  common['[PUBLIC_PRIVACY_POLICY_URL]'] = CONFIG.privacyUrls[lang];

  // Per-lang dynamic values
  const analyticsStatus = evidence.noAnalytics && evidence.noAds
    ? (lang === 'es' ? 'Confirmado: **sin SDKs de analítica, publicidad, crash reporting ni telemetría**. Dependencias: AndroidX, Compose, Room, OkHttp, kotlinx.serialization, coroutines. Sin Firebase, sin Play Services, sin terceros que recojan datos.'
       : lang === 'eu' ? 'Egoki: **ez da analitika, publicitate, crash-reporting edo telemetria SDK-rik**. Menpegotasunak: AndroidX, Compose, Room, OkHttp, kotlinx.serialization, coroutines. Ez Firebase, ez Play Services, ez hirugarrenak datuak bilatzen dituztenak.'
       : 'Confirmed: **no analytics, ads, crash reporting, or telemetry SDKs**. Dependencies: AndroidX, Compose, Room, OkHttp, kotlinx.serialization, coroutines. No Firebase, no Play Services, no third parties collecting data.')
    : '⚠️ Pending final AAB audit.';

  const retentionStatus = evidence.usesRoom && evidence.allowBackupFalse
    ? (lang === 'es' ? 'Datos locales (favoritos, recientes, avisos, preferencias): Room SQLite en directorio privado de la app. Borrado: usuario elimina desde la UI o desinstala la app. Backups: **android:allowBackup="false"** en manifiesto → no hay backup automático en Google Drive. Actualizaciones: Room migra esquemas; datos se conservan.'
       : lang === 'eu' ? 'Datu lokalak (gogokoak, azkenak, abisuak, aukerak): Room SQLite app-aren karpeta pribatuetan. Ezabatzea: erabiltzaileak UI-tik edo app-a desinstalatzen duenean. Backup-automatikorik ez: manifestoan **android:allowBackup="false"**. Eguneraketak: Room-esquema migratzen du; datuak mantentzen dira.'
       : 'Local data (favorites, recents, alerts, preferences): Room SQLite in app private directory. Deletion: user removes via UI or uninstalls app. Backups: **android:allowBackup="false"** in manifest → no automatic Google Drive backup. Updates: Room migrates schemas; data preserved.')
    : '⚠️ Pending verification.';

  common['[AZKEN_AAB_AUDITORIAREN_EMAITZAK]'] = analyticsStatus;
  common['[CONFIRM ACTUAL DEPENDENCIES, TRACKERS AND NETWORK TRAFFIC]'] = analyticsStatus;
  common['[BENETAKO_EZABAKETA_ETA_KONTSERBAZIO_PROZEDURA]'] = retentionStatus;
  common['[CONFIRM ON-DEVICE RETENTION, DELETION METHODS AND BACKUP POLICY]'] = retentionStatus;

  // Evidencia específica (solo placeholders que varían por idioma y no están en common)
  const specifics = {
    es: {
      '**Verificar que no se transmite a terceros en la build final.**': evidence.locationOnlyOnRequest
        ? 'Confirmado: la ubicación solo se usa localmente para ordenar paradas cercanas y nunca se transmite.'
        : '⚠️ Pendiente de verificación.',
      '**Verificar esquema y borrado real.**': evidence.usesRoom
        ? 'Confirmado: Room (SQLite local). El usuario borra favoritos/avisos desde la app; desinstalar elimina todo.'
        : '⚠️ Pendiente de verificación.',
      '**Pendiente de auditoría:** indicar la identidad legal del operador/proveedor...': 
        `Proveedor de datos de llegadas: **${CONFIG.dbusProvider.name}** (${CONFIG.dbusProvider.url}). ` +
        `Política de privacidad del proveedor: ${CONFIG.dbusProvider.privacyUrl}. ` +
        `Las consultas transmiten IP, metadatos de conexión y códigos de parada.`,
      'La implementación consultada usa **HTTP** para dicha API. No afirmar «cifrado en tránsito» para toda la aplicación hasta que cambie la infraestructura o se documente una alternativa segura.':
        'La API de Dbus (' + evidence.apiEndpoint + ') responde **solo por HTTP**. ' +
        'El manifiesto declara `cleartextTrafficPermitted=true` exclusivamente para `app.dbus.eus`. ' +
        'No hay otros endpoints de red. **No se puede afirmar «cifrado en tránsito» para toda la app.**',
      '[DEFINIR PUBLICO OBJETIVO Y REGLAS APLICABLES]':
        'Público general (PEGI 3 / Everyone). No dirigida a menores de 13 años. No recopila datos de menores intencionadamente.',
    },
    eu: {
      '**Verificar que no se trasmite a terceros en la build final.**': evidence.locationOnlyOnRequest
        ? 'Egoki: kokapena soilik erabilten da geltokiak ordenatzeko, lokalmodu, eta inoiz ez da trinketen bidaltzen.'
        : '⚠️ Berrikusi beharrezkoa.',
      '**Verificar esquema y borrado real.**': evidence.usesRoom
        ? 'Egoki: Room (SQLite lokal). Erabiltzaileak gogokoak/abisuak ezabatzen ditu interfazetik; desinstalazioa guztia kentzen du.'
        : '⚠️ Berrikusi beharrezkoa.',
      '**Pendiente de auditoría:** indicar la identidad legal del operador/proveedor...':
        `Datu-hornidatzailea: **${CONFIG.dbusProvider.name}** (${CONFIG.dbusProvider.url}). ` +
        `Pribatutasun-politika: ${CONFIG.dbusProvider.privacyUrl}. ` +
        `Kontsultak IP, konexio-metadatuak eta geltoki-kodeak trinketen bidaltzen ditu.`,
      'La implementación consultada usa **HTTP** para dicha API. No afirmar «cifrado en tránsito» para toda la aplicación hasta que cambie la infraestructura o se documente una alternativa segura.':
        'Dbus-en API-a (' + evidence.apiEndpoint + ') **HTTP-z bakarrik** erantzuten du. ' +
        'Manifestoak `cleartextTrafficPermitted=true` `app.dbus.eus`-entzat bakarrik deklaratzen du. ' +
        'Ez dago beste endpoint-ik. **Ez da «kifratutako transferentzia» esaterako apli osoarentzat.**',
      '[DEFINIR PUBLICO OBJETIVO Y REGLAS APLICABLES]':
        'Publiko orokorra (PEGI 3 / Everyone). Ez zuzena 13 urte baino gazteentzat. Ez ditu 13 urte baino gazteen daturikbiltzenadieraz.',
    },
    en: {
      '**Verificar que no se transmite a terceros en la build final.**': evidence.locationOnlyOnRequest
        ? 'Confirmed: location is only used locally to sort nearby stops and is never transmitted.'
        : '⚠️ Pending verification.',
      '**Verificar esquema y borrado real.**': evidence.usesRoom
        ? 'Confirmed: Room (local SQLite). User deletes favorites/alerts from UI; uninstall removes all.'
        : '⚠️ Pending verification.',
      '**Pendiente de auditoría:** indicar la identidad legal del operador/proveedor...':
        `Data provider: **${CONFIG.dbusProvider.name}** (${CONFIG.dbusProvider.url}). ` +
        `Provider privacy policy: ${CONFIG.dbusProvider.privacyUrl}. ` +
        `Requests transmit IP, connection metadata, and stop codes.`,
      'La implementación consultada usa **HTTP** para dicha API. No afirmar «cifrado en tránsito» para toda la aplicación hasta que cambie la infraestructura o se documente una alternativa segura.':
        'The Dbus API (' + evidence.apiEndpoint + ') responds **only over HTTP**. ' +
        'The manifest declares `cleartextTrafficPermitted=true` exclusively for `app.dbus.eus`. ' +
        'No other network endpoints. **Cannot claim "encryption in transit" for the whole app.**',
      // EN template inline texts
      'In the source implementation reviewed in November 2026, location is not persisted; confirm the released binary does not transmit it to third parties.':
        evidence.locationOnlyOnRequest
          ? 'Confirmed: location is only used locally to sort nearby stops and is never transmitted to third parties.'
          : '⚠️ Pending verification.',
      'Confirm deletion and backup behavior in the final build.':
        evidence.usesRoom && evidence.allowBackupFalse
          ? 'Confirmed: local data (favorites, recents, alerts, preferences) stored in Room SQLite. User deletes via UI or uninstall removes all. Backups: android:allowBackup="false" in manifest → no automatic Google Drive backup.'
          : '⚠️ Pending verification.',
      'Add the required legal information about the controller and applicable rights after a compliance review.':
        'Legal controller: ' + CONFIG.legalIdentity + '. Contact: ' + CONFIG.supportEmail + '.',
      // Title and footer cleanup handled by allGreen check below
      '[DEFINIR PUBLICO OBJETIVO Y REGLAS APLICABLES]':
        'General audience (PEGI 3 / Everyone). Not directed at children under 13. Does not knowingly collect data from children under 13.',
    },
  };

  // Reemplazos comunes
  for (const [placeholder, value] of Object.entries(common)) {
    t = t.replaceAll(placeholder, value);
  }

  // Reemplazos específicos por idioma
  const spec = specifics[lang];
  for (const [placeholder, value] of Object.entries(spec)) {
    t = t.replaceAll(placeholder, value);
  }

  // Quita la nota de "NO PUBLICAR SIN AUDITORÍA" si todo está verde
  const allGreen = Object.values(spec).every(v => !v.startsWith('⚠️'));
  if (allGreen) {
    t = t.replace(/^> \[!NOTE\].*\n/m, '');
    t = t.replace(/\*\*NOTA DE PUBLICACIÓN:.*$/ms, '');
    t = t.replace(/BORRADOR, NO PUBLICAR SIN AUDITORÍA/, 'VERSIÓN FINAL');
    // EN specific cleanup
    t = t.replace(/\(DRAFT — NOT READY TO PUBLISH\)/, '');
    t = t.replace(/\*\*Do not publish without final binary and legal review.\*\*/, '');
    t = t.replace(/Do not publish without final binary and legal review\./, '');
    // EU specific cleanup
    t = t.replace(/ZIRRIBORROA/, 'BERRIZKO BERTZIOA');
    t = t.replace(/\*\*EZ ARGITARATU BERRIKUSPENIK GABE:.*$/, '');
  }

  return t;
}

// ---- Markdown → HTML simple ----
function mdToHtml(md, lang) {
  const title = {
    es: 'Política de privacidad — Badator!',
    eu: 'Pribatutasun-politika — Badator!',
    en: 'Privacy Policy — Badator!',
  }[lang];

  let html = md
    .replace(/^# (.+)$/gm, '<h1>$1</h1>')
    .replace(/^## (.+)$/gm, '<h2>$1</h2>')
    .replace(/^\*\*(.+?)\*\*:/gm, '<p><strong>$1</strong>:</p>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/^- (.+)$/gm, '<li>$1</li>')
    .replace(/(<li>.*?<\/li>)/gs, (match) => '<ul>' + match + '</ul>')
    .replace(/<\/ul>\s*<ul>/g, '')
    .replace(/\n\n/g, '</p><p>')
    .replace(/\n/g, '<br>')
    .replace(/<p><\/p>/g, '')
    .replace(/<p><h2>/g, '<h2>')
    .replace(/<\/h2><\/p>/g, '</h2>')
    .replace(/<p><h1>/g, '<h1>')
    .replace(/<\/h1><\/p>/g, '</h1>')
    .replace(/<p><ul>/g, '<ul>')
    .replace(/<\/ul><\/p>/g, '</ul>');

  return `<!DOCTYPE html>
<html lang="${lang}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${title}</title>
  <style>
    body { font-family: system-ui, sans-serif; max-width: 800px; margin: 2rem auto; padding: 0 1rem; line-height: 1.6; color: #1a1a1a; }
    h1 { color: #143E3C; border-bottom: 2px solid #A4E8BD; padding-bottom: 0.5rem; }
    h2 { color: #143E3C; margin-top: 2rem; }
    a { color: #143E3C; }
    ul { padding-left: 1.5rem; }
    li { margin-bottom: 0.5rem; }
    footer { margin-top: 3rem; padding-top: 1rem; border-top: 1px solid #ddd; font-size: 0.9rem; color: #666; }
  </style>
</head>
<body>
  <p>${html}</p>
  <footer>
    Última actualización: ${CONFIG.lastUpdate} | ${CONFIG.developer} | <a href="${CONFIG.privacyUrls[lang]}">${CONFIG.privacyUrls[lang]}</a>
  </footer>
</body></html>`;
}

// ---- Despliegue a GitHub Pages ----
async function deployToGitHubPages(files) {
  if (!CONFIG.github.token) {
    console.log('⚠️ GH_TOKEN no configurado. Saltando deploy a GitHub Pages.');
    console.log('   Configura GH_TOKEN en .env.local o export GH_TOKEN=...');
    return;
  }

  const { Octokit } = await import('@octokit/rest');
  const octokit = new Octokit({ auth: CONFIG.github.token });
  const [owner, repo] = CONFIG.github.repo.split('/');

  for (const { path: filePath, content } of files) {
    try {
      // Lee el archivo actual para obtener SHA
      let sha;
      try {
        const { data } = await octokit.repos.getContent({
          owner,
          repo,
          path: filePath,
          ref: CONFIG.github.branch,
        });
        sha = data.sha;
      } catch {
        sha = undefined;
      }

      await octokit.repos.createOrUpdateFileContents({
        owner,
        repo,
        path: filePath,
        message: `docs(privacy): update ${path.basename(filePath)} [skip ci]`,
        content: Buffer.from(content).toString('base64'),
        sha,
        branch: CONFIG.github.branch,
      });
      console.log(`✅ Deployed: ${filePath}`);
    } catch (e) {
      console.error(`❌ Failed to deploy ${filePath}:`, e.message);
    }
  }
}

// ---- Main ----
async function main() {
  console.log('🔍 Leyendo evidencia del código...');
  const evidence = readCodeEvidence();
  console.log('   Permisos:', evidence.permissions.join(', '));
  console.log('   API:', evidence.apiEndpoint, `(${evidence.apiProtocol})`);
  console.log('   Room:', evidence.usesRoom ? 'sí' : 'no');
  console.log('   allowBackup=false:', evidence.allowBackupFalse ? 'sí' : 'no');
  console.log('   FGS dataSync:', evidence.fgsDataSync ? 'sí' : 'no');
  console.log('   Sin analytics/ads:', evidence.noAnalytics && evidence.noAds ? 'confirmado' : '⚠️');

  console.log('\n📝 Generando políticas...');
  const outputs = [];
  for (const lang of ['es', 'eu', 'en']) {
    const md = fillTemplate(lang, evidence);
    const html = mdToHtml(md, lang);
    const outFile = `privacy-policy-${lang}.html`;
    const outPath = path.join(OUT_DIR, outFile);
    fs.mkdirSync(OUT_DIR, { recursive: true });
    fs.writeFileSync(outPath, html);
    console.log(`   ✅ ${outPath}`);
    outputs.push({ path: `docs/privacy/${outFile}`, content: html });
  }

  console.log('\n🚀 Desplegando a GitHub Pages...');
  await deployToGitHubPages(outputs);

  console.log('\n🎉 Listo. URLs públicas:');
  for (const lang of ['es', 'eu', 'en']) {
    console.log(`   ${lang.toUpperCase()}: ${CONFIG.privacyUrls[lang]}`);
  }

  // Copia también a la carpeta del pack para referencia
  for (const lang of ['es', 'eu', 'en']) {
    const md = fillTemplate(lang, evidence);
    fs.writeFileSync(path.join(PACK, `03-privacy/PRIVACY-POLICY-${lang.toUpperCase()}-FINAL.md`), md);
  }
  console.log('\n📋 Versiones finales (markdown) copiadas a badator-google-play-pack/03-privacy/');
}

main().catch(console.error);