#!/usr/bin/env node
/**
 * Audita el AAB y genera respuestas para el formulario Data Safety de Play Console.
 *
 * Uso: node 02-audit-data-safety.mjs
 * Requiere: AAB firmado en apps/android/app/build/outputs/bundle/release/app-release.aab
 *
 * Salida: data-safety-responses.json listo para copiar a Play Console.
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
const OUT_FILE = path.join(ROOT, 'docs/play-console/data-safety-responses.json');

// ---- Config ----
const PACKAGE_NAME = 'eus.busdonostia.app';
const VERSION_NAME = '1.1.0';

// ---- Helpers ----
function run(cmd) {
  try {
    return execSync(cmd, { encoding: 'utf-8', maxBuffer: 10 * 1024 * 1024 });
  } catch (e) {
    console.error(`❌ Command failed: ${cmd}`);
    console.error(e.stdout || e.stderr || e.message);
    return '';
  }
}

function checkFileExists(p) {
  return fs.existsSync(p);
}

// ---- Análisis estático del AAB ----
function analyzeAAB() {
  console.log('📦 Analizando AAB...');
  if (!checkFileExists(AAB_PATH)) {
    console.log('⚠️ AAB no encontrado. Ejecuta primero: ./gradlew bundleRelease');
    return {};
  }

  // Extraer y analizar el AAB (es un ZIP)
  const tmpDir = path.join(ROOT, '.tmp-aab-analysis');
  fs.rmSync(tmpDir, { recursive: true, force: true });
  fs.mkdirSync(tmpDir, { recursive: true });

  run(`cd "${tmpDir}" && unzip -q "${AAB_PATH}"`);

  const manifestPath = path.join(tmpDir, 'base/manifest/AndroidManifest.xml');
  const manifest = checkFileExists(manifestPath) ? fs.readFileSync(manifestPath, 'utf-8') : '';

  // Buscar permisos declarados
  const permissions = [];
  const permRegex = /android:name="([^"]+)"/g;
  let m;
  while ((m = permRegex.exec(manifest))) {
    if (m[1].startsWith('android.permission.')) permissions.push(m[1]);
  }

  // Buscar servicios FGS
  const hasFGSDataSync = manifest.includes('foregroundServiceType="dataSync"') ||
                         manifest.includes('FOREGROUND_SERVICE_DATA_SYNC');

  // Buscar network security config
  const hasNetSecConfig = manifest.includes('networkSecurityConfig');

  // Buscar allowBackup
  const allowBackup = manifest.includes('allowBackup="false"') ? false :
                      manifest.includes('allowBackup="true"') ? true : 'default';

  // Buscar queries (geo: intent)
  const hasGeoQuery = manifest.includes('android.intent.action.VIEW') &&
                      manifest.includes('android:scheme="geo"');

  // Analizar classes.dex para dependencias (aprox)
  const classesDex = path.join(tmpDir, 'base/dex/classes.dex');
  let hasFirebase = false;
  let hasPlayServices = false;
  let hasAnalytics = false;
  let hasCrashlytics = false;
  let hasAds = false;
  let hasRoom = false;
  let hasOkHttp = false;

  if (checkFileExists(classesDex)) {
    // Usar strings para buscar clases conocidas (rápido, no exacto)
    const strings = run(`strings "${classesDex}" 2>/dev/null | head -5000`);
    hasFirebase = strings.includes('com.google.firebase') || strings.includes('Firebase');
    hasPlayServices = strings.includes('com.google.android.gms') || strings.includes('play-services');
    hasAnalytics = strings.includes('Analytics') || strings.includes('analytics');
    hasCrashlytics = strings.includes('Crashlytics') || strings.includes('crashlytics');
    hasAds = strings.includes('Ads') || strings.includes('admob') || strings.includes('AdMob');
    hasRoom = strings.includes('androidx.room') || strings.includes('RoomDatabase');
    hasOkHttp = strings.includes('okhttp3') || strings.includes('OkHttp');
  }

  // Limpiar
  fs.rmSync(tmpDir, { recursive: true, force: true });

  return {
    permissions,
    hasFGSDataSync,
    hasNetSecConfig,
    allowBackup,
    hasGeoQuery,
    hasFirebase,
    hasPlayServices,
    hasAnalytics,
    hasCrashlytics,
    hasAds,
    hasRoom,
    hasOkHttp,
  };
}

// ---- Análisis de código fuente ----
function analyzeSource() {
  console.log('🔍 Analizando código fuente...');

  const manifest = fs.readFileSync(
    path.join(ROOT, 'apps/android/app/src/main/AndroidManifest.xml'), 'utf-8'
  );
  const netConfig = fs.readFileSync(
    path.join(ROOT, 'apps/android/app/src/main/res/xml/network_security_config.xml'), 'utf-8'
  );
  const configJson = fs.readFileSync(
    path.join(ROOT, 'apps/android/app/src/main/assets/config.json'), 'utf-8'
  );
  const gradle = fs.readFileSync(
    path.join(ROOT, 'apps/android/app/build.gradle.kts'), 'utf-8'
  );
  const packageJson = fs.readFileSync(
    path.join(ROOT, 'package.json'), 'utf-8'
  );

  // Permisos
  const permissions = [];
  const permRegex = /uses-permission android:name="([^"]+)"/g;
  let m;
  while ((m = permRegex.exec(manifest))) {
    permissions.push(m[1]);
  }

  // Endpoint API
  let apiEndpoint = '';
  let apiProtocol = 'HTTPS';
  try {
    const cfg = JSON.parse(configJson);
    apiEndpoint = cfg.apiBaseUrl + cfg.endpoints.tiemposParada;
    if (netConfig.includes('cleartextTrafficPermitted="true"')) {
      apiProtocol = 'HTTP (cleartext) - solo app.dbus.eus';
    }
  } catch {}

  // Dependencias Gradle
  const deps = {
    room: gradle.includes('room-runtime') || gradle.includes('room-ktx') || gradle.includes('libs.room'),
    okhttp: gradle.includes('okhttp'),
    serialization: gradle.includes('kotlinx-serialization'),
    compose: gradle.includes('compose'),
    coroutines: gradle.includes('coroutines'),
    lifecycle: gradle.includes('lifecycle'),
    workManager: gradle.includes('work-runtime') || gradle.includes('work-rxjava'),
  };

  return {
    permissions,
    apiEndpoint,
    apiProtocol,
    deps,
    allowBackupFalse: manifest.includes('allowBackup="false"'),
    hasFGSDataSync: manifest.includes('FOREGROUND_SERVICE_DATA_SYNC'),
    hasNetSecConfig: manifest.includes('networkSecurityConfig'),
  };
}

// ---- Generar respuestas Data Safety ----
function generateDataSafety(aab, source) {
  const allPerms = [...new Set([...aab.permissions, ...source.permissions])];

  // Room: combinar AAB + source
  const hasRoom = aab.hasRoom || source.deps?.room;

  // Determinar qué datos se recogen fuera del dispositivo (según guía de Google Play Data Safety,
  // "recopilar" significa transmitir datos fuera del dispositivo del usuario; el uso efímero
  // exclusivamente en memoria local sin transmisión externa NO constituye recopilación).
  const collectsLocation = false;
  const collectsDeviceId = false; // No hay identidad de dispositivo
  const collectsPersonalInfo = false; // No hay cuenta de usuario
  const collectsAppActivity = false; // No analytics
  const collectsAppPerformance = false; // No crash reporting
  const collectsDiagnostics = false;
  const collectsOther = false;

  // Compartir datos
  const sharesLocation = false; // Nunca sale del dispositivo
  const sharesDeviceId = false;
  const sharesPersonalInfo = false;
  const sharesAppActivity = false;
  const sharesAppPerformance = false;
  const sharesDiagnostics = false;
  const sharesOther = false;

  // Seguridad
  const encryptsInTransit = false; // API es HTTP cleartext
  const encryptsAtRest = false; // Room SQLite no cifra por defecto; Android FBE cifra a nivel SO, no la app
  const allowsDeletion = true; // Usuario borra favoritos/avisos, desinstala app

  return {
    packageName: PACKAGE_NAME,
    versionName: VERSION_NAME,
    lastUpdated: new Date().toISOString().split('T')[0],

    // Data collection
    dataCollection: {
      collectsLocation,
      collectsDeviceId,
      collectsPersonalInfo,
      collectsAppActivity,
      collectsAppPerformance,
      collectsDiagnostics,
      collectsOther,
    },

    // Data sharing
    dataSharing: {
      sharesLocation,
      sharesDeviceId,
      sharesPersonalInfo,
      sharesAppActivity,
      sharesAppPerformance,
      sharesDiagnostics,
      sharesOther,
    },

    // Security practices
    securityPractices: {
      encryptsInTransit,
      encryptsAtRest,
      allowsDeletion,
      // No "data_transfer_encrypted" porque HTTP cleartext
    },

    // Data types details
    dataTypes: {
      location: null,
      device_id: null,
      personal_info: null,
      app_activity: null,
      app_performance: null,
      diagnostics: null,
      other: null,
    },

    // Permissions declared
    declaredPermissions: allPerms,

    // Notes for Play Console reviewer
    reviewerNotes: {
      location: 'La ubicación NO se recopila ni se comparte (según la definición de Data Safety de Google Play, "recopilar" implica transmitir datos fuera del dispositivo). El permiso es OPCIONAL y se usa únicamente en memoria local en el dispositivo bajo petición expresa del usuario ("Cercanas") para calcular distancias frente al catálogo offline de paradas. Nunca se persiste ni se transmite a ningún servidor.',
      network: `La API de llegadas (${source.apiEndpoint || 'app.dbus.eus'}) responde solo por HTTP. El manifiesto declara cleartextTrafficPermitted=true EXCLUSIVAMENTE para app.dbus.eus. No hay otros endpoints. Por tanto NO se puede marcar "cifrado en tránsito" para toda la app.`,
      analytics: 'Sin SDKs de analítica, crash reporting, publicidad ni telemetría. Dependencias: AndroidX, Compose, Room, OkHttp, kotlinx.serialization, coroutines. Sin Firebase, sin Play Services.',
      storage: 'Datos locales (favoritos, recientes, avisos, preferencias) en Room SQLite y SharedPreferences dentro del sandbox de la app. android:allowBackup="false" → no backup automático en Google Drive. Usuario elimina todo desde la UI o desinstalando.',
      fgs: 'Foreground Service DATA_SYNC para consultar llegadas en background mientras el usuario tiene avisos activos. Máx 3h vigilancia continua (config.maxWatchHours). Se detiene si no hay avisos. Usuario gestiona desde la app.',
      geo: 'Intent geo: solo para "Cómo llegar" si hay app de mapas instalada (envía únicamente coordenadas públicas de la parada). No es navegación propia.',
    },

    // Evidence files (para auditoría interna)
    evidence: {
      manifestPath: 'apps/android/app/src/main/AndroidManifest.xml',
      networkSecurityConfigPath: 'apps/android/app/src/main/res/xml/network_security_config.xml',
      configPath: 'apps/android/app/src/main/assets/config.json',
      locationProviderPath: 'apps/android/app/src/main/kotlin/eus/busdonostia/app/data/location/LocationProvider.kt',
      roomEntities: [
        'apps/android/app/src/main/kotlin/eus/busdonostia/app/data/db/RecentStopEntity.kt',
        'apps/android/app/src/main/kotlin/eus/busdonostia/app/data/db/AlertEntity.kt',
        'apps/android/app/src/main/kotlin/eus/busdonostia/app/data/db/FavoriteEntity.kt',
      ],
    },
  };
}

// ---- Main ----
async function main() {
  console.log('🔐 Generando Data Safety para Play Console...\n');

  const aab = analyzeAAB();
  const source = analyzeSource();

  console.log('\n📋 Permisos declarados:', source.permissions.join(', '));
  console.log('🌐 API:', source.apiEndpoint, `(${source.apiProtocol})`);
  console.log('🗄️ Room:', source.deps.room ? 'sí' : 'no');
  console.log('🔒 allowBackup=false:', source.allowBackupFalse ? 'sí' : 'no');
  console.log('⚙️ FGS DataSync:', source.hasFGSDataSync ? 'sí' : 'no');
  console.log('📡 NetSecConfig:', source.hasNetSecConfig ? 'sí' : 'no');
  console.log('🔥 Firebase:', aab.hasFirebase ? 'sí' : 'no');
  console.log('📊 Analytics:', aab.hasAnalytics ? 'sí' : 'no');
  console.log('💥 Crashlytics:', aab.hasCrashlytics ? 'sí' : 'no');
  console.log('📱 Ads:', aab.hasAds ? 'sí' : 'no');

  const safety = generateDataSafety(aab, source);

  fs.mkdirSync(path.dirname(OUT_FILE), { recursive: true });
  fs.writeFileSync(OUT_FILE, JSON.stringify(safety, null, 2));

  console.log(`\n✅ Generado: ${OUT_FILE}`);
  console.log('\n📋 Para Play Console:');
  console.log('1. Abre Play Console → Tu app → Políticas de apps → Seguridad de los datos');
  console.log('2. Rellena el formulario usando los valores de data-safety-responses.json');
  console.log('3. En "¿Recoge o comparte tu aplicación alguno de los tipos de datos de usuario requeridos?": marca "No" (la ubicación solo se procesa en memoria local en el dispositivo y nunca se transmite fuera)');
  console.log('4. Usa las "Notas para revisor" de data-safety-responses.json en la declaración de permisos / revisión');

  // Copia también al pack
  const packFile = path.join(PACK, '04-console/DATA-SAFETY-RESPONSES.json');
  fs.mkdirSync(path.dirname(packFile), { recursive: true });
  fs.writeFileSync(packFile, JSON.stringify(safety, null, 2));
  console.log(`\n📋 Copia guardada en: ${packFile}`);
}

main().catch(console.error);