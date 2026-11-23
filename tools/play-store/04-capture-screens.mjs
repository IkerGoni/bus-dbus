#!/usr/bin/env node
/**
 * Captura 6 pantallas × 3 idiomas (ES/EU/EN) = 18 PNGs desde emulador real vía ADB.
 *
 * Uso:
 *   node tools/play-store/04-capture-screens.mjs
 *
 * Salida:
 *   - build/outputs/play-store/screenshots/screenshot-NN-{es,eu,en}.png
 *   - $PLAY_PACK_DIR/02-creative/screenshots/screenshot-NN-{es,eu,en}.png (por defecto ~/Desktop/badator-google-play-pack)
 */

import fs from 'fs';
import os from 'node:os';
import path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const OUT_DIR = path.join(ROOT, 'build/outputs/play-store/screenshots');
const PACK = process.env.PLAY_PACK_DIR || path.join(os.homedir(), 'Desktop', 'badator-google-play-pack');
const PACK_DIR = path.join(PACK, '02-creative/screenshots');
const PACKAGE_NAME = 'eus.busdonostia.app';

function resolveAdb() {
  if (process.env.ADB) return process.env.ADB;
  if (process.env.ANDROID_HOME) {
    const sdkAdb = path.join(process.env.ANDROID_HOME, 'platform-tools', 'adb');
    if (fs.existsSync(sdkAdb)) return sdkAdb;
  }
  const macFallback = '/opt/homebrew/share/android-commandlinetools/platform-tools/adb';
  if (fs.existsSync(macFallback)) return macFallback;
  return 'adb';
}

const ADB = resolveAdb();

function run(cmd) {
  try {
    return execSync(cmd, { encoding: 'utf-8', maxBuffer: 10 * 1024 * 1024 });
  } catch (e) {
    console.error(`❌ Error ejecutando: ${cmd}`);
    if (e.stdout) console.error(e.stdout);
    if (e.stderr) console.error(e.stderr);
    throw e;
  }
}

function adb(cmd) {
  return run(`${ADB} ${cmd}`);
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

const SCREENS = [
  { id: '01', name: 'Llegadas en detalle' },
  { id: '02', name: 'Configurar aviso' },
  { id: '03', name: 'Inicio con favoritas' },
  { id: '04', name: 'Paradas cercanas' },
  { id: '05', name: 'Buscar parada/línea' },
  { id: '06', name: 'Gestión de avisos' },
];

const LANGS = [
  { code: 'es', name: 'Español' },
  { code: 'eu', name: 'Euskera' },
  { code: 'en', name: 'English' },
];

async function capture(screenId, langCode) {
  const filename = `screenshot-${screenId}-${langCode}.png`;
  const outPath = path.join(OUT_DIR, filename);
  const packPath = path.join(PACK_DIR, filename);

  const buffer = execSync(`${ADB} exec-out screencap -p`);
  fs.writeFileSync(outPath, buffer);
  fs.copyFileSync(outPath, packPath);

  const sizeKb = (buffer.length / 1024).toFixed(1);
  console.log(`    📸 Capturada ${filename} (${sizeKb} KB) -> OK`);
}

async function prepareDevice() {
  console.log('📱 Verificando emulador y permisos...');
  const devices = adb('devices').trim().split('\n').slice(1).filter(l => l.includes('device'));
  if (devices.length === 0) {
    throw new Error('No hay emulador o dispositivo conectado vía ADB.');
  }
  console.log(`  ✅ Dispositivo conectado: ${devices[0].split('\t')[0]}`);

  // Permisos necesarios
  adb(`shell pm grant ${PACKAGE_NAME} android.permission.ACCESS_FINE_LOCATION`);
  adb(`shell pm grant ${PACKAGE_NAME} android.permission.ACCESS_COARSE_LOCATION`);
  adb(`shell pm grant ${PACKAGE_NAME} android.permission.POST_NOTIFICATIONS`);

  // Posición GPS fija en Donostia (Boulevard)
  adb('emu geo fix -1.9839 43.3213');
  console.log('  ✅ GPS fijado en Donostia (-1.9839, 43.3213)');

  // Asegurar que la base de datos tiene favoritas y alerta para las capturas
  try {
    adb(`shell "su 0 sqlite3 /data/data/${PACKAGE_NAME}/databases/busdonostia.db \\"INSERT OR IGNORE INTO favorites VALUES ('STOP:238:', 'STOP', '238', '', 1795456503881);\\""`);
    adb(`shell "su 0 sqlite3 /data/data/${PACKAGE_NAME}/databases/busdonostia.db \\"INSERT OR IGNORE INTO favorites VALUES ('STOP:34:', 'STOP', '34', '', 1795456504947);\\""`);
    adb(`shell "su 0 sqlite3 /data/data/${PACKAGE_NAME}/databases/busdonostia.db \\"INSERT OR REPLACE INTO alerts VALUES ('alert-1', '19', 'Boulevard 19', '16', 'Igeldo', 5, 1, $(date +%s)000);\\""`);
    console.log('  ✅ Semilla SQLite verificada (favoritas + alerta activa)');
  } catch (e) {
    console.warn('  ⚠️ Aviso: no se pudo sembrar sqlite directamente (quizás no root), continuando.');
  }
}

async function setLanguageAndLaunch(langCode) {
  console.log(`\n🌐 Configurando idioma: ${langCode.toUpperCase()}...`);
  adb(`shell cmd locale set-app-locales ${PACKAGE_NAME} --locales ${langCode}`);
  adb(`shell am force-stop ${PACKAGE_NAME}`);
  await sleep(1000);
  adb(`shell am start -n ${PACKAGE_NAME}/.MainActivity`);
  await sleep(3500); // espera a carga de datos en vivo de Dbus
}

async function runCaptureLoop() {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.mkdirSync(PACK_DIR, { recursive: true });

  await prepareDevice();

  for (const lang of LANGS) {
    console.log(`\n==================================================`);
    console.log(`🚀 INICIANDO CAPTURAS: ${lang.name} (${lang.code})`);
    console.log(`==================================================`);

    await setLanguageAndLaunch(lang.code);

    // 1. Screen 03: Inicio con favoritas
    console.log('  -> Capturando 03: Inicio con favoritas...');
    await adb('shell input tap 135 2232'); // Tab Inicio
    await sleep(1500);
    await capture('03', lang.code);

    // 2. Screen 01: Llegadas en detalle (tocar Boulevard 19 en favoritas)
    console.log('  -> Capturando 01: Llegadas en detalle...');
    await adb('shell input tap 540 1550'); // Card Boulevard 19
    await sleep(2000);
    await capture('01', lang.code);

    // 3. Screen 02: Configurar aviso (tocar botón Crear aviso en esta parada)
    console.log('  -> Capturando 02: Configurar aviso...');
    await adb('shell input tap 540 2040'); // Botón Crear aviso
    await sleep(1500);
    await capture('02', lang.code);

    // Volver a Home
    await adb('shell input keyevent 4'); // Back desde sheet
    await sleep(800);
    await adb('shell input keyevent 4'); // Back desde detalle parada
    await sleep(800);

    // 4. Screen 04: Paradas cercanas
    console.log('  -> Capturando 04: Paradas cercanas...');
    await adb('shell input tap 405 2232'); // Tab Paradas
    await sleep(1000);
    await adb('shell input tap 1007 211'); // Botón refrescar ubicación si hiciera falta
    await sleep(2000);
    await capture('04', lang.code);

    // 5. Screen 05: Buscar parada/línea
    console.log('  -> Capturando 05: Buscar parada/línea...');
    await adb('shell input tap 400 350'); // Campo de búsqueda
    await sleep(800);
    await adb('shell input text "Boulevard"');
    await sleep(1500);
    await adb('shell input keyevent 111'); // Escape / Ocultar teclado
    await sleep(800);
    await capture('05', lang.code);

    // Salir de búsqueda
    await adb('shell input keyevent 4');
    await sleep(800);

    // 6. Screen 06: Gestión de avisos
    console.log('  -> Capturando 06: Gestión de avisos...');
    await adb('shell input tap 945 2232'); // Tab Avisos
    await sleep(1500);
    await capture('06', lang.code);

    // Volver a Home
    await adb('shell input tap 135 2232');
    await sleep(1000);
  }

  console.log('\n🎉 ¡Capturas completadas con éxito!');
  console.log(`📁 Directorio local: ${OUT_DIR}`);
  console.log(`📁 Directorio pack: ${PACK_DIR}`);
}

runCaptureLoop().catch(err => {
  console.error('\n❌ Error durante el proceso de capturas:', err);
  process.exit(1);
});
