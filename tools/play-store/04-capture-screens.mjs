#!/usr/bin/env node
/**
 * Captura 6 pantallas × 3 idiomas (ES/EU/EN) = 18 PNGs desde emulador real.
 *
 * Uso:
 *   1. Arranca emulador: `emulator -avd dbus36`
 *   2. Instala build debug: `adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk`
 *   3. Ejecuta: `node 04-capture-screens.mjs`
 *
 * Requiere: Playwright (`npm i -D @playwright/test`), adb, emulador API 36 corriendo.
 *
 * Salida: build/outputs/play-store/screenshots/screenshot-NN-{es,eu,en}.png
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';
import { chromium } from 'playwright';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const OUT_DIR = path.join(ROOT, 'build/outputs/play-store/screenshots');
const APK_PATH = path.join(ROOT, 'apps/android/app/build/outputs/apk/debug/app-debug.apk');
const PACKAGE_NAME = 'eus.busdonostia.app';

// ---- Config de capturas (orden = importancia Play Console) ----
const SCREENS = [
  {
    id: '01',
    name: 'Llegadas en detalle',
    es: 'Mira cuándo llega',
    eu: 'Ikusi noiz iritsiko den',
    en: 'See when bus arrives',
    // Navegación: Abrir app → tocar parada "Boulevard 9" (o primera de la lista) → detalle
    navigate: async (page, lang) => {
      await page.waitForSelector('text=/Pr[oó]ximas|Hurrengo|Upcoming/i', { timeout: 10000 });
      // Toca la primera parada de la lista (HomeScreen muestra favoritas o cercanas)
      const firstStop = await page.locator('[data-testid="stop-row"], .stop-row, text=/Boulevard|Bulevar|Plaza|Kale/i').first();
      if (await firstStop.count() > 0) await firstStop.tap();
      else {
        // Fallback: busca cualquier elemento clicable que parezca parada
        await page.locator('android.view.View').filter({ hasText: /min/i }).first().tap();
      }
      await page.waitForTimeout(1500); // carga detalle
    },
  },
  {
    id: '02',
    name: 'Configurar aviso',
    es: 'Recibe un aviso',
    eu: 'Jaso abisu bat',
    en: 'Get an alert',
    // En detalle de parada → tocar 🔔 en primera llegada → elegir 2 min → Guardar
    navigate: async (page, lang) => {
      await page.waitForSelector('text=/Pr[oó]ximas|Hurrengo|Upcoming/i', { timeout: 10000 });
      const firstStop = await page.locator('[data-testid="stop-row"], .stop-row, text=/Boulevard|Bulevar|Plaza|Kale/i').first();
      if (await firstStop.count() > 0) await firstStop.tap();
      await page.waitForTimeout(1500);
      // Toca botón de campana (crear aviso) en la primera llegada
      const bell = page.locator('button:has-text("🔔"), [data-testid="alert-bell"], button[aria-label*="aviso"], button[aria-label*="abisu"], button[aria-label*="alert"]').first();
      if (await bell.count() > 0) {
        await bell.tap();
        await page.waitForTimeout(1000);
        // Selecciona 2 min si hay selector
        const twoMin = page.locator('text=/2 min|2min|2\'/i').first();
        if (await twoMin.count() > 0) await twoMin.tap();
        // Guardar
        const saveBtn = page.locator('button:has-text("Guardar"), button:has-text("Gorde"), button:has-text("Save")').first();
        if (await saveBtn.count() > 0) await saveBtn.tap();
        await page.waitForTimeout(1000);
      }
    },
  },
  {
    id: '03',
    name: 'Inicio con favoritas',
    es: 'Tus paradas, a mano',
    eu: 'Zure geltokiak, eskuin',
    en: 'Your stops, at hand',
    // HomeScreen con favoritas visibles
    navigate: async (page, lang) => {
      // Ya estamos en home al arrancar
      await page.waitForSelector('text=/Pr[oó]ximas|Hurrengo|Upcoming|Favoritas|Gogokoak|Favorites/i', { timeout: 10000 });
      await page.waitForTimeout(1000);
    },
  },
  {
    id: '04',
    name: 'Paradas cercanas',
    es: 'Encuentra paradas cercanas',
    eu: 'Aurkitu geltoki hurbilak',
    en: 'Find nearby stops',
    // Pestaña "Cercanas" → conceder ubicación si pide
    navigate: async (page, lang) => {
      await page.waitForSelector('text=/Pr[oó]ximas|Hurrengo|Upcoming/i', { timeout: 10000 });
      // Toca pestaña Cercanas / Hurbilekoak / Nearby
      const nearbyTab = page.locator('text=/Cercanas|Hurbilekoak|Nearby/i').first();
      if (await nearbyTab.count() > 0) await nearbyTab.tap();
      await page.waitForTimeout(2000);
      // Si pide permiso de ubicación, conceder (via adb grant)
    },
  },
  {
    id: '05',
    name: 'Buscar parada/línea',
    es: 'Busca por parada o línea',
    eu: 'Bilatu geltoki edo linea',
    en: 'Search by stop or line',
    // Toca búsqueda → escribe "Boulevard" → resultados
    navigate: async (page, lang) => {
      await page.waitForSelector('text=/Pr[oó]ximas|Hurrengo|Upcoming/i', { timeout: 10000 });
      // Toca icono lupa / búsqueda
      const searchBtn = page.locator('[data-testid="search-button"], button[aria-label*="buscar"], button[aria-label*="bilatu"], button[aria-label*="search"]').first();
      if (await searchBtn.count() > 0) {
        await searchBtn.tap();
        await page.waitForTimeout(500);
        // Escribe en campo búsqueda
        const input = page.locator('input[type="text"], [data-testid="search-input"], android.widget.EditText').first();
        if (await input.count() > 0) {
          await input.fill('Boulevard');
          await page.waitForTimeout(1500);
        }
      }
    },
  },
  {
    id: '06',
    name: 'Gestión de avisos',
    es: 'Todo bajo control',
    eu: 'Guztia kontrolpean',
    en: 'Everything under control',
    // Pestaña "Avisos" / "Abisuak" / "Alerts"
    navigate: async (page, lang) => {
      await page.waitForSelector('text=/Pr[oó]ximas|Hurrengo|Upcoming/i', { timeout: 10000 });
      const alertsTab = page.locator('text=/Avisos|Abisuak|Alerts/i').first();
      if (await alertsTab.count() > 0) await alertsTab.tap();
      await page.waitForTimeout(1000);
    },
  },
];

// ---- Idiomas ----
const LANGS = [
  { code: 'es', locale: 'es-ES', name: 'Español' },
  { code: 'eu', locale: 'eu-ES', name: 'Euskera' },
  { code: 'en', locale: 'en-US', name: 'English' },
];

function run(cmd) {
  try {
    return execSync(cmd, { encoding: 'utf-8', maxBuffer: 10 * 1024 * 1024 });
  } catch (e) {
    console.error(`❌ ${cmd}`);
    console.error(e.stdout || e.stderr || e.message);
    return '';
  }
}

async function setLanguage(code) {
  console.log(`  🌐 Cambiando idioma a ${code}...`);
  run(`adb shell am broadcast -a android.intent.action.LOCALE_CHANGED --es lang ${code}`);
  // Fuerza recreación de actividad
  run(`adb shell am force-stop ${PACKAGE_NAME}`);
  await new Promise(r => setTimeout(r, 1500));
}

async function grantPermissions() {
  console.log('  🔐 Concediendo permisos...');
  run(`adb shell pm grant ${PACKAGE_NAME} android.permission.ACCESS_FINE_LOCATION`);
  run(`adb shell pm grant ${PACKAGE_NAME} android.permission.ACCESS_COARSE_LOCATION`);
  run(`adb shell pm grant ${PACKAGE_NAME} android.permission.POST_NOTIFICATIONS`);
  // Foreground service no necesita grant explícito
}

async function launchApp(page) {
  console.log('  🚀 Lanzando app...');
  run(`adb shell monkey -p ${PACKAGE_NAME} -c android.intent.category.LAUNCHER 1`);
  await page.waitForTimeout(3000); // espera a que cargue
}

async function captureScreen(page, screen, lang) {
  const filename = `screenshot-${screen.id}-${lang.code}.png`;
  const filepath = path.join(OUT_DIR, filename);
  
  console.log(`  📸 Capturando: ${screen.name} (${lang.code})...`);
  
  // Navega a la pantalla
  await screen.navigate(page, lang);
  
  // Pequeña espera extra para render
  await page.waitForTimeout(1000);
  
  // Captura
  await page.screenshot({ path: filepath, fullPage: true });
  
  console.log(`    ✅ ${filepath}`);
  return filepath;
}

async function main() {
  console.log('📱 Capturando pantallas para Play Store...\n');

  // Verificar emulador
  const devices = run('adb devices').trim().split('\n').slice(1).filter(l => l.includes('device'));
  if (devices.length === 0) {
    console.error('❌ No hay emulador/dispositivo conectado. Arranca: emulator -avd dbus36');
    process.exit(1);
  }
  console.log(`✅ Dispositivo: ${devices[0].split('\t')[0]}`);

  // Verificar APK
  if (!fs.existsSync(APK_PATH)) {
    console.error(`❌ APK no encontrado: ${APK_PATH}`);
    console.log('   Ejecuta: ./gradlew assembleDebug');
    process.exit(1);
  }
  console.log(`✅ APK: ${APK_PATH}`);

  // Instalar/reinstalar APK
  console.log('📦 Instalando APK debug...');
  run(`adb install -r -d "${APK_PATH}"`);

  // Conceder permisos
  await grantPermissions();

  fs.mkdirSync(OUT_DIR, { recursive: true });

  // Conectar Playwright al WebView de la app (usando Chrome DevTools Protocol via adb)
  // NOTA: Playwright no puede automatizar nativamente apps Android sin WebView.
  // Alternativa: usar Appium o UIAutomator2. Aquí usamos screencap vía adb como fallback.
  
  console.log('\n⚠️ Playwright no automatiza apps Android nativas directamente.');
  console.log('   Usando adb screencap como fallback (requiere navegación manual o script UIAutomator).');
  console.log('   Para automatización completa, usa Appium + este script como guía.\n');

  // Fallback: capturas manuales guiadas
  for (const lang of LANGS) {
    await setLanguage(lang.code);
    await launchApp(null);
    
    console.log(`\n🌐 IDIOMA: ${lang.name} (${lang.code})`);
    console.log('   Navega manualmente a cada pantalla y pulsa ENTER para capturar...');
    
    for (const screen of SCREENS) {
      console.log(`\n   📋 ${screen.id}. ${screen.name} [${lang[lang.code]}]`);
      console.log(`      Acción: navega a "${screen.es}"`);
      
      const input = await new Promise(resolve => {
        console.log('      Pulsa ENTER cuando estés en la pantalla...');
        process.stdin.once('data', d => resolve(d.toString().trim()));
      });
      
      const filename = `screenshot-${screen.id}-${lang.code}.png`;
      const filepath = path.join(OUT_DIR, filename);
      run(`adb exec-out screencap -p > "${filepath}"`);
      console.log(`      ✅ ${filepath}`);
    }
  }

  // Copiar al pack
  const packDir = '/Users/dev/Desktop/badator-google-play-pack/02-creative/screenshots';
  fs.mkdirSync(packDir, { recursive: true });
  for (const file of fs.readdirSync(OUT_DIR)) {
    if (file.endsWith('.png')) {
      fs.copyFileSync(path.join(OUT_DIR, file), path.join(packDir, file));
    }
  }
  console.log(`\n📋 Copiados a badator-google-play-pack/02-creative/screenshots/`);
  console.log('\n🎉 Capturas completadas!');
}

main().catch(console.error);