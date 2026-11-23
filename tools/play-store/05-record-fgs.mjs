#!/usr/bin/env node
/**
 * Graba vídeo del flujo FGS (Foreground Service DataSync) para Play Console.
 *
 * Flujo obligatorio (guion Play Console):
 * 1. Abrir app → ir a detalle de parada con llegadas en vivo
 * 2. Tocar 🔔 en una llegada → elegir antelación (ej. 2 min) → "Guardar aviso"
 * 3. Mostrar notificación persistente "Vigilando L… en …" (FGS activo)
 * 4. Poner app en background (Home)
 * 5. Esperar notificación de llegada "L… → … en X min"
 * 6. Mostrar que el aviso desaparece de la lista y FGS se detiene
 *
 * Uso:
 *   1. Emulador corriendo: emulator -avd dbus36
 *   2. App instalada: adb install -r app-debug.apk
 *   3. Ejecutar: node 05-record-fgs.mjs
 *
 * Requiere: ffmpeg, adb, emulador API 36
 * Salida: build/outputs/play-store/fgs-demo.mp4
 * Subida opcional a YouTube (no listado) con youtube-upload o manual
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const OUT_DIR = path.join(ROOT, 'build/outputs/play-store');
const PACKAGE_NAME = 'eus.busdonostia.app';

const SCRIPT = `
# 🎬 GUION VÍDEO FGS - Badator! (para Play Console)

## Configuración previa
- Emulador API 36 (dbus36) corriendo
- App debug instalada con permisos concedidos
- Ubicación concedida (para que haya llegadas reales)
- Parada "Boulevard 9" (código 1) con llegadas en vivo

## Flujo a grabar (duración real: 2-4 min según antelación)

### 1. APERTURA + DETALLE PARADA (10-15 seg)
- Abrir app desde launcher
- Mostrar HomeScreen con favoritas/cercanas
- Tocar parada "Boulevard 9" → abre StopDetailScreen
- Esperar a que carguen llegadas (LIVE visible)

### 2. CREAR AVISO (15-20 seg)
- En StopDetailScreen: tocar 🔔 (campana) en la PRIMERA llegada (L28, 3 min, LIVE)
- Pantalla AlertEditScreen: elegir "2 min" de antelación
- Tocar "Guardar aviso" / "Gorde"
- Confirmar que aparece toast/snackbar "Aviso guardado"

### 3. NOTIFICACIÓN PERSISTENTE FGS (5-10 seg)
- Deslizar barra de notificaciones (swipe down desde top)
- MOSTRAR notificación: "Vigilando L28 en Boulevard 9"
- Explicar: "Esta notificación persistente = Foreground Service DataSync activo"
- Soltar barra de notificaciones

### 4. APP EN BACKGROUND (5 seg)
- Pulsar botón Home (círculo) → app en background
- Mostrar launcher / home del emulador
- Esperar 2-3 segundos

### 5. NOTIFICACIÓN DE LLEGADA (momento clave, duración real ~2-3 min)
- ESPERAR notificación: "L28 → Amara en 2 min" (o similar)
- Deslizar barra notificaciones y MOSTRAR la notificación de llegada
- Tocar notificación → abre app en StopDetailScreen
- Mostrar que el aviso YA NO está en la lista de avisos (pestaña Avisos vacía o sin ese aviso)

### 6. VERIFICACIÓN FGS DETENIDO (5 seg)
- Deslizar barra notificaciones → confirmar que NO hay "Vigilando…"
- Abrir app → pestaña Avisos → vacía / sin el aviso que acabamos de disparar

## Duración total estimada: 3-5 minutos (tiempo real de espera del bus)
## Formato: MP4, 1080p o 720p, 30 fps, audio opcional (narración en off)
## Subida: YouTube no listado → copiar URL → pegar en Play Console
`;

function run(cmd) {
  try {
    return execSync(cmd, { encoding: 'utf-8', maxBuffer: 10 * 1024 * 1024 });
  } catch (e) {
    console.error(`❌ ${cmd}`);
    console.error(e.stdout || e.stderr || e.message);
    return '';
  }
}

function checkFfmpeg() {
  try {
    run('ffmpeg -version');
    return true;
  } catch {
    return false;
  }
}

async function main() {
  console.log('🎬 Preparando grabación vídeo FGS para Play Console...\n');

  if (!checkFfmpeg()) {
    console.error('❌ ffmpeg no instalado. Instala: brew install ffmpeg');
    process.exit(1);
  }
  console.log('✅ ffmpeg disponible');

  const devices = run('adb devices').trim().split('\n').slice(1).filter(l => l.includes('device'));
  if (devices.length === 0) {
    console.error('❌ No hay emulador/dispositivo. Arranca: emulator -avd dbus36');
    process.exit(1);
  }
  console.log(`✅ Dispositivo: ${devices[0].split('\t')[0]}`);

  fs.mkdirSync(path.join(ROOT, 'build/outputs/play-store'), { recursive: true });

  // Guardar guion
  const scriptPath = path.join(ROOT, 'build/outputs/play-store/FGS_SCRIPT.md');
  fs.writeFileSync(scriptPath, SCRIPT);
  console.log(`\n📋 Guion guardado: ${scriptPath}`);

  console.log('\n📋 PRÓXIMOS PASOS MANUALES:');
  console.log('1. Arranca emulador: emulator -avd dbus36');
  console.log('2. Instala app: adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk');
  console.log('3. Concede permisos: adb shell pm grant eus.busdonostia.app android.permission.ACCESS_FINE_LOCATION');
  console.log('   adb shell pm grant eus.busdonostia.app android.permission.POST_NOTIFICATIONS');
  console.log('4. Abre app, ve a "Boulevard 9", espera llegadas LIVE');
  console.log('5. GRABA PANTALLA (Android: grabar pantalla nativo O scrcpy + ffmpeg):');
  console.log('   adb shell screenrecord --time-limit 300 --bit-rate 8000000 /sdcard/fgs-demo.mp4');
  console.log('   (pulsa Ctrl+C cuando termines el flujo)');
  console.log('6. Descarga: adb pull /sdcard/fgs-demo.mp4 build/outputs/play-store/fgs-demo.mp4');
  console.log('7. (Opcional) Recorta con ffmpeg: ffmpeg -i fgs-demo.mp4 -ss 0 -t 180 -c copy fgs-demo-trim.mp4');
  console.log('7. Sube a YouTube (no listado) y copia URL para Play Console');

  // Guardar también en pack
  const packDir = '/Users/dev/Desktop/badator-google-play-pack/02-creative';
  fs.mkdirSync(packDir, { recursive: true });
  fs.copyFileSync(path.join(ROOT, 'build/outputs/play-store/FGS_SCRIPT.md'), path.join(packDir, 'FGS_SCRIPT.md'));
  console.log(`\n📋 Guion copiado a badator-google-play-pack/02-creative/FGS_SCRIPT.md`);
}

main().catch(console.error);