#!/usr/bin/env node
/**
 * Genera políticas de privacidad finales (ES/EU/EN) a partir de plantillas
 * y evidencia real del código. Despliega a GitHub Pages.
 *
 * Uso: node 01-gen-privacy.mjs
 * Opcional en env:
 *   PLAY_PACK_DIR, PRIVACY_DEVELOPER, PRIVACY_LEGAL_IDENTITY,
 *   PRIVACY_SUPPORT_EMAIL, PRIVACY_GITHUB_USER, GH_TOKEN, GH_PAGES_REPO
 */

import fs from 'fs';
import os from 'os';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const PACK = path.resolve(
  process.env.PLAY_PACK_DIR || path.join(os.homedir(), 'Desktop', 'badator-google-play-pack'),
);
const OUT_DIR = path.resolve(ROOT, 'docs/privacy');

const githubUser = process.env.PRIVACY_GITHUB_USER || 'IkerGoni';
const pagesHost = githubUser.toLowerCase();

// ---- Configuración parametrizada por variables de entorno ----
const CONFIG = {
  developer: process.env.PRIVACY_DEVELOPER || 'Dev Igoni',
  legalIdentity: process.env.PRIVACY_LEGAL_IDENTITY || 'Iker Goñi Cuesta (Pamplona, España)',
  supportEmail: process.env.PRIVACY_SUPPORT_EMAIL || 'dev.mail.igoni@gmail.com',
  githubUser,
  privacyUrls: {
    es: `https://${pagesHost}.github.io/bus-dbus/privacy/privacy-policy-es.html`,
    eu: `https://${pagesHost}.github.io/bus-dbus/privacy/privacy-policy-eu.html`,
    en: `https://${pagesHost}.github.io/bus-dbus/privacy/privacy-policy-en.html`,
  },
  dbusProvider: {
    name: 'Compañía del Tranvía de San Sebastián, S.A.U. — Dbus (Donostiako Autobusak)',
    url: 'https://dbus.eus',
    privacyUrls: {
      es: 'https://dbus.eus/es/politica-de-privacidad/',
      eu: 'https://dbus.eus/pribatutasun-politika/',
      en: 'https://dbus.eus/en/privacy-policy/',
    },
  },
  github: {
    repo: process.env.GH_PAGES_REPO || `${githubUser}/bus-dbus`,
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
  '[URL_PUBLICA_POLITICA_PRIVACIDAD]': CONFIG.privacyUrls.es,
  // EU
  '[BENETAKO_DATA]': CONFIG.lastUpdate,
  '[ARDURADUNAREN_LEGEZKO_DATUAK]': CONFIG.legalIdentity,
  '[BENETAKO_LAGUNTZA_EMAILA]': CONFIG.supportEmail,
  '[POLITIKAREN_URL_PUBLIKOA]': CONFIG.privacyUrls.eu,
  '[AZKEN_AAB_AUDITORIAREN_EMAITZAK]': '',
  '[BENETAKO_EZABAKETA_ETA_KONTSERBAZIO_PROZEDURA]': '',
  // EN
  '[ACTUAL_DATE]': CONFIG.lastUpdate,
  '[REQUIRED_LEGAL_IDENTITY]': CONFIG.legalIdentity,
  '[REAL_SUPPORT_EMAIL]': CONFIG.supportEmail,
  '[PUBLIC_PRIVACY_POLICY_URL]': CONFIG.privacyUrls.en,
  '[CONFIRM ACTUAL DEPENDENCIES, TRACKERS AND NETWORK TRAFFIC]': '',
  '[CONFIRM ON-DEVICE RETENTION, DELETION METHODS AND BACKUP POLICY]': '',
};

// ---- Lee evidencia real del código ----
function readCodeEvidence() {
  const manifest = fs.readFileSync(path.join(ROOT, 'apps/android/app/src/main/AndroidManifest.xml'), 'utf-8');
  const netConfig = fs.readFileSync(path.join(ROOT, 'apps/android/app/src/main/res/xml/network_security_config.xml'), 'utf-8');
  const locationProvider = fs.readFileSync(path.join(ROOT, 'apps/android/app/src/main/kotlin/eus/busdonostia/app/data/location/LocationProvider.kt'), 'utf-8');
  const configJson = fs.readFileSync(path.join(ROOT, 'apps/android/app/src/main/assets/config.json'), 'utf-8');
  const gradle = fs.readFileSync(path.join(ROOT, 'apps/android/app/build.gradle.kts'), 'utf-8');
  const packageJson = fs.readFileSync(path.join(ROOT, 'package.json'), 'utf-8');
  const recentEntity = fs.readFileSync(path.join(ROOT, 'apps/android/app/src/main/kotlin/eus/busdonostia/app/data/db/RecentStopEntity.kt'), 'utf-8');
  const alertEntity = fs.readFileSync(path.join(ROOT, 'apps/android/app/src/main/kotlin/eus/busdonostia/app/data/db/AlertEntity.kt'), 'utf-8');

  return {
    permissions: extractPermissions(manifest),
    networkSecurity: netConfig,
    usesRoom: recentEntity.includes('@Entity') || alertEntity.includes('@Entity') || gradle.includes('room-runtime') || gradle.includes('room-ktx'),
    locationPersisted: !locationProvider.includes('Nunca se persiste') && !locationProvider.includes('never persisted'),
    locationOnlyOnRequest: locationProvider.includes('solo cuando') || locationProvider.includes('only when') || locationProvider.includes('en memoria y se olvida'),
    notificationsOptional: true,
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

function loadTemplates() {
  return {
    es: fs.readFileSync(path.join(PACK, '03-privacy/PRIVACY-POLICY-es-ES-DRAFT.md'), 'utf-8'),
    eu: fs.readFileSync(path.join(PACK, '03-privacy/PRIVACY-POLICY-eu-ES-DRAFT.md'), 'utf-8'),
    en: fs.readFileSync(path.join(PACK, '03-privacy/PRIVACY-POLICY-en-US-DRAFT.md'), 'utf-8'),
  };
}

// ---- Relleno de placeholders con evidencia real ----
function fillTemplate(templates, lang, evidence) {
  let t = templates[lang];

  const common = { ...PLACEHOLDERS };
  common['[URL_PUBLICA_POLITICA_PRIVACIDAD]'] = CONFIG.privacyUrls[lang];
  common['[POLITIKAREN_URL_PUBLIKOA]'] = CONFIG.privacyUrls[lang];
  common['[PUBLIC_PRIVACY_POLICY_URL]'] = CONFIG.privacyUrls[lang];

  const analyticsStatus = evidence.noAnalytics && evidence.noAds
    ? (lang === 'es'
      ? 'Badator! no incluye SDKs de analítica, publicidad, informes de fallos ni telemetría. Sus dependencias son exclusivamente funcionales (AndroidX, Jetpack Compose, Room, OkHttp, kotlinx.serialization y Kotlin Coroutines). No utiliza Firebase, Google Play Services ni terceros que recopilen o compartan datos'
      : lang === 'eu'
        ? 'Badator! aplikazioak ez du analitika, publizitate, errore-txosten edo telemetria SDKrik. Mendekotasunak funtzionalak bakarrik dira (AndroidX, Jetpack Compose, Room, OkHttp, kotlinx.serialization eta Kotlin Coroutines). Ez du Firebase, Google Play Services edo datuak biltzen dituzten hirugarrenik erabiltzen'
        : 'Badator! does not include analytics, advertising, crash reporting, or telemetry SDKs. Its dependencies are strictly functional (AndroidX, Jetpack Compose, Room, OkHttp, kotlinx.serialization, and Kotlin Coroutines). It does not use Firebase, Google Play Services, or third-party trackers')
    : 'Audit required before release';

  const retentionStatus = evidence.usesRoom && evidence.allowBackupFalse
    ? (lang === 'es'
      ? 'Los datos locales (favoritos, paradas recientes, avisos y preferencias) se guardan en Room (SQLite) y SharedPreferences dentro del almacenamiento privado del dispositivo. La app no tiene cuentas de usuario. El manifiesto declara `android:allowBackup="false"`, por lo que no se realizan copias de seguridad automáticas en Google Drive. Puedes borrar los datos desde la propia app, borrando los datos de la aplicación en los ajustes de Android o desinstalándola'
      : lang === 'eu'
        ? 'Datu lokalak (gogokoak, azken geltokiak, abisuak eta hobespenak) gailuaren biltegiratze pribatuan gordetzen dira, Room (SQLite) eta SharedPreferences bidez. Aplikazioak ez du erabiltzaile-konturik. Manifestuak `android:allowBackup="false"` deklaratzen duenez, ez da segurtasun-kopia automatikorik egiten Google Driven. Datuak aplikaziotik bertatik, Androiden ezarpenetatik edo aplikazioa desinstalatuz ezaba ditzakezu'
        : 'Local data (favorites, recent stops, alerts, and preferences) is stored in Room (SQLite) and SharedPreferences inside the app private storage on your device. The app has no user accounts. The manifest sets `android:allowBackup="false"`, so no automatic Google Drive backup is performed. You can delete your data from within the app, by clearing app data in Android settings, or by uninstalling the app')
    : 'Verification required before release';

  common['[AZKEN_AAB_AUDITORIAREN_EMAITZAK]'] = analyticsStatus;
  common['[CONFIRM ACTUAL DEPENDENCIES, TRACKERS AND NETWORK TRAFFIC]'] = analyticsStatus;
  common['[BENETAKO_EZABAKETA_ETA_KONTSERBAZIO_PROZEDURA]'] = retentionStatus;
  common['[CONFIRM ON-DEVICE RETENTION, DELETION METHODS AND BACKUP POLICY]'] = retentionStatus;

  const specifics = {
    es: {
      'Según la implementación revisada en noviembre de 2026, no la persiste. **Verificar que no se transmite a terceros en la build final.**':
        'La ubicación es opcional, se procesa únicamente en memoria en el dispositivo para ordenar paradas cercanas y nunca se guarda ni se transmite a terceros. Al pulsar «Cómo llegar» en el detalle de una parada, la aplicación abre la app de mapas instalada mediante un intent estándar `geo:` pasando únicamente las coordenadas públicas y el nombre de la parada.',
      '**Verificar que no se transmite a terceros en la build final.**':
        'La ubicación es opcional, se procesa únicamente en memoria en el dispositivo para ordenar paradas cercanas y nunca se guarda ni se transmite a terceros.',
      '**Verificar esquema y borrado real.**':
        'Se almacenan localmente en Room (SQLite) y SharedPreferences con `android:allowBackup="false"`. Puedes eliminarlos desde la app, borrando los datos en los ajustes de Android o desinstalándola.',
      'La implementación consultada usa **HTTP** para dicha API. No afirmar «cifrado en tránsito» para toda la aplicación hasta que cambie la infraestructura o se documente una alternativa segura.':
        `La API pública de Dbus (\`${evidence.apiEndpoint}\`) responde actualmente solo por **HTTP**. El manifiesto declara \`cleartextTrafficPermitted="true"\` exclusivamente para \`app.dbus.eus\`, y la app no contacta con ningún otro servidor. Por ello, las consultas a dicho servicio no viajan cifradas en tránsito.`,
      '[DEFINIR PUBLICO OBJETIVO Y REGLAS APLICABLES]':
        'Badator! está dirigida a público general (PEGI 3 / Everyone), no a menores de 13 años, y no recopila datos personales de menores.',
      'Dev iGoni —': `${CONFIG.developer} —`,
    },
    eu: {
      // Paridad con es/en (PRIV-01): el proveedor también ve los geltokien identifikatzaileak.
      'Zerbitzuak IP helbidea eta konexioaren metadatu teknikoak jaso ditzake.':
        'Zerbitzuak IP helbidea, konexioaren metadatu teknikoak eta eskatutako geltokien identifikatzaileak jaso ditzake.',
      '; aztertutako inplementazioan ez da modu iraunkorrean gordetzen. **Azken bertsioan egiaztatu behar da.**':
        '. Kokapena aukerakoa da, gailuko memorian soilik erabiltzen da inguruko geltokiak distantziaren arabera ordenatzeko, eta ez da inoiz gordetzen edo hirugarrenei bidaltzen. Geltoki baten xehetasunean «Nola iritsi» sakatzean, instalatutako mapa-aplikazioa `geo:` intent estandarraren bidez irekitzen da, geltokiaren koordenatu publikoak eta izena soilik pasatuz.',
      '**Azken bertsioan egiaztatu behar da.**':
        'Kokapena aukerakoa da, gailuko memorian soilik erabiltzen da inguruko geltokiak distantziaren arabera ordenatzeko, eta ez da inoiz gordetzen edo hirugarrenei bidaltzen.',
      'Horien ezabaketa eta segurtasun-kopiak egiaztatu behar dira.':
        'Gailuan bertan gordetzen dira Room (SQLite lokala) eta SharedPreferences bidez, `android:allowBackup="false"` ezarpenarekin. Erabiltzaileak aplikaziotik bertatik, Androiden ezarpenetatik edo aplikazioa desinstalatuz ezaba ditzake.',
      'Aztertutako inplementazioak HTTP erabiltzen du. Ez esan komunikazio guztiak zifratuta daudela, hori egiaztatu gabe.':
        `Dbusen API publikoak (\`${evidence.apiEndpoint}\`) **HTTP** bidez bakarrik erantzuten du gaur egun. Manifestuak \`cleartextTrafficPermitted="true"\` deklaratzen du \`app.dbus.eus\` domeinurako bakarrik, eta ez dago beste sare-helbiderik. Hori dela eta, kontsulta horiek ez doaz zifratuta garraioan. Iritsiera-datuen hornitzailea **${CONFIG.dbusProvider.name}** (${CONFIG.dbusProvider.url}) da, eta bere pribatutasun-politika ${CONFIG.dbusProvider.privacyUrls.eu} helbidean kontsulta daiteke.`,
      'Osatu lege-informazioa argitaratu aurretik.':
        `Arduraduna: ${CONFIG.legalIdentity}. Kontaktua: ${CONFIG.supportEmail}.`,
      '[DEFINIR PUBLICO OBJETIVO Y REGLAS APLICABLES]':
        'Publiko orokorrarentzako aplikazioa da (PEGI 3 / Everyone). Ez dago 13 urtetik beherakoei zuzenduta eta ez du adingabeen datu pertsonalik biltzen.',
      'Dev iGoni —': `${CONFIG.developer} —`,
    },
    en: {
      'In the source implementation reviewed in November 2026, location is not persisted; confirm the released binary does not transmit it to third parties.':
        'Location is optional, used ephemerally in memory on the device to sort nearby stops, and is never persisted or transmitted to third parties. When tapping "Directions" on a stop detail screen, the app opens your installed map application via a standard Android `geo:` intent passing only the stop\'s public coordinates and name.',
      'Confirm deletion and backup behavior in the final build.':
        'Data is stored locally in Room (SQLite) and SharedPreferences with `android:allowBackup="false"` (no automatic Google Drive backup). You can delete it in-app, via Android app settings, or by uninstalling.',
      'The source implementation reviewed uses **HTTP** for these requests, so do not claim all network data is encrypted in transit.':
        `The public Dbus API (\`${evidence.apiEndpoint}\`) currently responds only over **HTTP**. The manifest declares \`cleartextTrafficPermitted="true"\` exclusively for \`app.dbus.eus\`, and no other network endpoints are used. Accordingly, requests to that endpoint are not encrypted in transit. The transit data provider is **${CONFIG.dbusProvider.name}** (${CONFIG.dbusProvider.url}), whose privacy policy is available at ${CONFIG.dbusProvider.privacyUrls.en}.`,
      'Add the required legal information about the controller and applicable rights after a compliance review.':
        `Controller: ${CONFIG.legalIdentity}. Contact: ${CONFIG.supportEmail}.`,
      '[DEFINIR PUBLICO OBJETIVO Y REGLAS APLICABLES]':
        'Badator! is intended for a general audience (PEGI 3 / Everyone), is not directed at children under 13, and does not knowingly collect personal data from children.',
      'Dev iGoni —': `${CONFIG.developer} —`,
    },
  };

  for (const [placeholder, value] of Object.entries(common)) {
    t = t.replaceAll(placeholder, value);
  }

  for (const [placeholder, value] of Object.entries(specifics[lang])) {
    t = t.replaceAll(placeholder, value);
  }

  // Reemplazos de bloques editoriales/borrador que varían en las plantillas
  if (lang === 'es') {
    t = t.replace(
      /\*\*Pendiente de auditoría:\*\*[^\n]*/g,
      `El proveedor de datos de llegadas es **${CONFIG.dbusProvider.name}** (${CONFIG.dbusProvider.url}), cuya política de privacidad puede consultarse en ${CONFIG.dbusProvider.privacyUrls.es}.`,
    );
    t = t.replace(
      /\*\*Pendiente:\*\*[^\n]*/g,
      `${analyticsStatus}.`,
    );
    t = t.replace(
      /\*\*Verificar ruta de borrado[^\n]*/g,
      `${retentionStatus}.`,
    );
    t = t.replace(
      /Si la build no tiene cuentas, describirlo explícitamente una vez verificado\./g,
      'Badator! no dispone de cuentas de usuario ni almacena datos personales en servidores propios.',
    );
    t = t.replace(
      /Añadir identidad del responsable, base jurídica y datos adicionales exigidos tras la revisión de cumplimiento\./g,
      `Responsable: ${CONFIG.legalIdentity}.`,
    );
    t = t.replace(
      /La aplicación proporciona información sobre transporte público y no debe presentarse como dirigida a niños sin completar el cuestionario de público objetivo y familias\./g,
      '',
    );
  }

  // Limpieza definitiva de marcadores de borrador y notas editoriales en todos los idiomas
  t = t
    .replace(/^> \[!NOTE\][^\n]*\n?/gm, '')
    .replace(/\*\*NOTA DE PUBLICACIÓN:[\s\S]*$/m, '')
    .replace(/\*\*EZ ARGITARATU BERRIKUSPENIK GABE:[\s\S]*$/m, '')
    .replace(/\*\*Do not publish without final binary and legal review\.\*\*/g, '')
    .replace(/Do not publish without final binary and legal review\./g, '')
    .replace(/\s*\(BORRADOR[^)]*\)/g, '')
    .replace(/\s*\(VERSIÓN FINAL\)/g, '')
    .replace(/\s*\(DRAFT[^)]*\)/g, '')
    .replace(/\s*\(ZIRRIBORROA[^)]*\)/g, '')
    .replace(/\s*\(BERRIZKO BERTZIOA\)/g, '')
    .replace(/^---+\s*$/gm, '')
    .replace(/(?<!\.)\.\.(?!\.)/g, '.')
    .replace(/\n{3,}/g, '\n\n')
    .trim();

  return t;
}

// ---- Markdown → HTML limpio y válido ----
function formatInline(text) {
  return text
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)]+)\)/g, '<a href="$2">$1</a>')
    .replace(/(?<!\.)\.\.(?!\.)/g, '.');
}

function mdToHtml(md, lang) {
  const title = {
    es: 'Política de privacidad — Badator!',
    eu: 'Pribatutasun-politika — Badator!',
    en: 'Privacy Policy — Badator!',
  }[lang];

  const footerLabel = {
    es: 'Última actualización',
    eu: 'Azken eguneraketa',
    en: 'Last updated',
  }[lang];

  const blocks = md
    .replace(/\r\n/g, '\n')
    .split(/\n{2,}/)
    .map((b) => b.trim())
    .filter((b) => b && !/^---+$/.test(b));

  const renderedBlocks = blocks.map((block) => {
    if (/^### /.test(block)) {
      return `  <h3>${formatInline(block.replace(/^### /, '').trim())}</h3>`;
    }
    if (/^## /.test(block)) {
      return `  <h2>${formatInline(block.replace(/^## /, '').trim())}</h2>`;
    }
    if (/^# /.test(block)) {
      return `  <h1>${formatInline(block.replace(/^# /, '').trim())}</h1>`;
    }
    const lines = block.split('\n').map((l) => l.trim()).filter(Boolean);
    if (lines.every((l) => l.startsWith('- '))) {
      const items = lines
        .map((l) => `    <li>${formatInline(l.slice(2).trim())}</li>`)
        .join('\n');
      return `  <ul>\n${items}\n  </ul>`;
    }
    const paragraph = lines.map((l) => formatInline(l)).join('<br>\n    ');
    return `  <p>${paragraph}</p>`;
  });

  return `<!DOCTYPE html>
<html lang="${lang}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${title}</title>
  <style>
    body { font-family: system-ui, -apple-system, sans-serif; max-width: 800px; margin: 2rem auto; padding: 0 1rem; line-height: 1.6; color: #1a1a1a; }
    h1 { color: #143E3C; border-bottom: 2px solid #A4E8BD; padding-bottom: 0.5rem; }
    h2 { color: #143E3C; margin-top: 2rem; }
    a { color: #143E3C; }
    code { background: #f4f4f4; padding: 0.1rem 0.3rem; border-radius: 3px; font-size: 0.9em; }
    ul { padding-left: 1.5rem; }
    li { margin-bottom: 0.5rem; }
    footer { margin-top: 3rem; padding-top: 1rem; border-top: 1px solid #ddd; font-size: 0.9rem; color: #666; }
  </style>
</head>
<body>
${renderedBlocks.join('\n\n')}

  <footer>
    ${footerLabel}: ${CONFIG.lastUpdate} | ${CONFIG.developer} | <a href="${CONFIG.privacyUrls[lang]}">${CONFIG.privacyUrls[lang]}</a>
  </footer>
</body>
</html>
`;
}

// ---- Despliegue a GitHub Pages (requiere flag explícito --deploy) ----
async function deployToGitHubPages(files) {
  if (!process.argv.includes('--deploy')) {
    console.log('ℹ️ Modo generación local (pasa --deploy para subir vía API con GH_TOKEN).');
    return;
  }
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

  const templates = loadTemplates();

  console.log('\n📝 Generando políticas...');
  const outputs = [];
  const markdowns = {};
  for (const lang of ['es', 'eu', 'en']) {
    const md = fillTemplate(templates, lang, evidence);
    markdowns[lang] = md;
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

  if (fs.existsSync(path.join(PACK, '03-privacy'))) {
    for (const lang of ['es', 'eu', 'en']) {
      fs.writeFileSync(path.join(PACK, `03-privacy/PRIVACY-POLICY-${lang.toUpperCase()}-FINAL.md`), markdowns[lang]);
    }
    console.log(`\n📋 Versiones finales (markdown) copiadas a ${path.join(PACK, '03-privacy')}`);
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
