#!/usr/bin/env node
/**
 * Automatiza validación, preflight y subida de Badator! (AAB firmado) a Google Play via `gplay`.
 *
 * Uso:
 *   node tools/play-store/10-release-gplay.mjs [opciones]
 *
 * Opciones:
 *   --track <track>         Canal destino (default: internal; ej. alpha, beta, production)
 *   --status <status>       completed | draft | inProgress | halted (default: completed)
 *   --notes <ruta>          JSON de release notes (default: build/outputs/play-store/release-notes-<versionName>.json si existe)
 *   --version-name <nombre> Nombre de versión opcional para la ficha del release en Play
 *   --skip-build            No ejecuta `./gradlew check bundleRelease` (usa el AAB existente)
 *   --dry-run               Previsualiza el release con `gplay --dry-run release ...` sin enviar cambios
 *   --wait                  Espera a que Play termine el procesado
 */

import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const ANDROID_DIR = path.join(ROOT, 'apps/android');
const GRADLE_FILE = path.join(ANDROID_DIR, 'app/build.gradle.kts');
const AAB_PATH = path.join(ANDROID_DIR, 'app/build/outputs/bundle/release/app-release.aab');
const PACKAGE_NAME = 'eus.busdonostia.app';

function parseArgs(argv) {
  const opts = {
    track: 'internal',
    status: 'completed',
    notes: null,
    versionName: null,
    skipBuild: false,
    dryRun: false,
    wait: false,
  };

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    switch (arg) {
      case '--track':
        opts.track = requireArgValue(argv, ++i, '--track');
        break;
      case '--status':
        opts.status = requireArgValue(argv, ++i, '--status');
        break;
      case '--notes':
        opts.notes = requireArgValue(argv, ++i, '--notes');
        break;
      case '--version-name':
        opts.versionName = requireArgValue(argv, ++i, '--version-name');
        break;
      case '--skip-build':
        opts.skipBuild = true;
        break;
      case '--dry-run':
        opts.dryRun = true;
        break;
      case '--wait':
        opts.wait = true;
        break;
      default:
        throw new Error(`Opción desconocida: ${arg}`);
    }
  }
  return opts;
}

function requireArgValue(argv, index, flag) {
  const value = argv[index];
  if (!value || value.startsWith('--')) {
    throw new Error(`Falta el valor para ${flag}`);
  }
  return value;
}

function readGradleVersion() {
  const gradleText = fs.readFileSync(GRADLE_FILE, 'utf-8');
  const codeMatch = gradleText.match(/versionCode\s*=\s*(\d+)/);
  const nameMatch = gradleText.match(/versionName\s*=\s*"([^"]+)"/);
  if (!codeMatch || !nameMatch) {
    throw new Error(`No se pudo leer versionCode/versionName de ${GRADLE_FILE}`);
  }
  return {
    versionCode: Number.parseInt(codeMatch[1], 10),
    versionName: nameMatch[1],
  };
}

function runInherit(cmd, args, cwd = ROOT) {
  execFileSync(cmd, args, { cwd, stdio: 'inherit' });
}

function checkGplayAuth() {
  const raw = execFileSync('gplay', ['auth', 'status'], { encoding: 'utf-8' });
  const status = JSON.parse(raw);
  if (!status.profile) {
    throw new Error('gplay no tiene un perfil activo configurado (ejecuta `gplay auth login ...`).');
  }
  return status.profile;
}

function main() {
  const opts = parseArgs(process.argv.slice(2));
  const { versionCode, versionName } = readGradleVersion();
  const profile = checkGplayAuth();

  console.log(`📱 Paquete: ${PACKAGE_NAME}`);
  console.log(`🏷️  Versión Gradle: ${versionName} (versionCode ${versionCode})`);
  console.log(`🔑 Perfil gplay activo: ${profile}`);
  console.log(`🚀 Destino: track=${opts.track}, status=${opts.status}${opts.dryRun ? ' [DRY-RUN]' : ''}\n`);

  if (!opts.skipBuild) {
    console.log('🔨 Compilando y verificando bundle release (./gradlew check bundleRelease)...');
    runInherit('./gradlew', ['check', 'bundleRelease'], ANDROID_DIR);
  }

  if (!fs.existsSync(AAB_PATH)) {
    throw new Error(`No existe el AAB firmado en ${path.relative(ROOT, AAB_PATH)}`);
  }

  console.log('\n🔍 Ejecutando gplay preflight...');
  runInherit('gplay', ['preflight', '--file', AAB_PATH]);

  const defaultNotesPath = path.join(ROOT, `build/outputs/play-store/release-notes-${versionName}.json`);
  const notesPath = opts.notes ? path.resolve(ROOT, opts.notes) : (fs.existsSync(defaultNotesPath) ? defaultNotesPath : null);
  if (opts.notes && !fs.existsSync(notesPath)) {
    throw new Error(`No existe el fichero de release notes: ${opts.notes}`);
  }

  const gplayArgs = [];
  if (opts.dryRun) gplayArgs.push('--dry-run');
  gplayArgs.push(
    'release',
    '--package',
    PACKAGE_NAME,
    '--track',
    opts.track,
    '--status',
    opts.status,
    '--bundle',
    AAB_PATH,
  );
  if (opts.versionName) {
    gplayArgs.push('--version-name', opts.versionName);
  }
  if (notesPath) {
    gplayArgs.push('--release-notes', `@${notesPath}`);
    console.log(`📝 Release notes: ${path.relative(ROOT, notesPath)}`);
  }
  if (opts.wait) {
    gplayArgs.push('--wait');
  }

  console.log('\n📤 Ejecutando gplay release...');
  runInherit('gplay', gplayArgs);
  console.log('\n✅ Proceso completado.');
}

try {
  main();
} catch (err) {
  console.error(`\n❌ ${err instanceof Error ? err.message : String(err)}`);
  process.exit(1);
}
