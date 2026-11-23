#!/usr/bin/env node
/**
 * Genera un directorio de metadata en formato Fastlane/GPP que entiende `gplay`
 * a partir del pack de publicación de Google Play.
 *
 * Salida: build/outputs/play-store/metadata/
 *   <locale>/title.txt
 *   <locale>/short_description.txt
 *   <locale>/full_description.txt
 *   <locale>/changelogs/<versionCode>.txt
 *   <locale>/images/phoneScreenshots/*.png
 *   <locale>/images/featureGraphic.png
 *   <locale>/images/icon.png
 *
 * Uso: node tools/play-store/09-gplay-metadata.mjs
 * Luego: gplay metadata validate --dir build/outputs/play-store/metadata
 *        gplay preflight --file apps/android/app/build/outputs/bundle/release/app-release.aab \
 *          --listings-dir build/outputs/play-store/metadata
 */

import fs from 'fs';
import os from 'node:os';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const PACK = process.env.PLAY_PACK_DIR || path.join(os.homedir(), 'Desktop', 'badator-google-play-pack');
const OUT = path.join(ROOT, 'build/outputs/play-store/metadata');
const OUT_TEXT = path.join(ROOT, 'build/outputs/play-store/metadata-validate');
const OUT_SHOTS = path.join(ROOT, 'build/outputs/play-store/screenshots-gplay');
const OUT_NOTES = path.join(ROOT, 'build/outputs/play-store/release-notes.json');
const VERSION_CODE = process.env.VERSION_CODE || '2';

const fields = JSON.parse(fs.readFileSync(path.join(PACK, '07-store-fields.json'), 'utf-8'));

const SHOTS = {
  'es-ES': 'es',
  'eu-ES': 'eu',
  'en-US': 'en',
};

for (const dir of [OUT, OUT_TEXT, OUT_SHOTS]) fs.rmSync(dir, { recursive: true, force: true });
const releaseNotes = [];

for (const [locale, data] of Object.entries(fields.locales)) {
  const base = path.join(OUT, locale);
  const images = path.join(base, 'images');
  const phone = path.join(images, 'phoneScreenshots');
  fs.mkdirSync(path.join(base, 'changelogs'), { recursive: true });
  fs.mkdirSync(phone, { recursive: true });

  const textBase = path.join(OUT_TEXT, locale);
  fs.mkdirSync(textBase, { recursive: true });
  for (const [file, value] of [
    ['title.txt', data.title],
    ['short_description.txt', data.short],
    ['full_description.txt', data.full],
  ]) {
    fs.writeFileSync(path.join(base, file), `${value}\n`, 'utf-8');
    fs.writeFileSync(path.join(textBase, file), `${value}\n`, 'utf-8');
  }
  fs.writeFileSync(path.join(base, 'changelogs', `${VERSION_CODE}.txt`), `${data.release}\n`, 'utf-8');
  fs.writeFileSync(path.join(base, 'video_url.txt'), '\n', 'utf-8');
  releaseNotes.push({ language: locale, text: data.release });

  const shotsBase = path.join(OUT_SHOTS, locale, 'phoneScreenshots');
  fs.mkdirSync(shotsBase, { recursive: true });

  const code = SHOTS[locale];
  for (let n = 1; n <= 6; n++) {
    const name = `screenshot-${String(n).padStart(2, '0')}.png`;
    const src = path.join(PACK, '02-creative/screenshots', `screenshot-${String(n).padStart(2, '0')}-${code}.png`);
    if (!fs.existsSync(src)) throw new Error(`Falta captura: ${src}`);
    fs.copyFileSync(src, path.join(phone, name));
    fs.copyFileSync(src, path.join(shotsBase, name));
  }

  fs.copyFileSync(path.join(PACK, '02-creative/feature-graphic-1024x500.png'), path.join(images, 'featureGraphic.png'));
  fs.copyFileSync(path.join(PACK, '02-creative/icon-512.png'), path.join(images, 'icon.png'));

  console.log(`✅ ${locale}: textos + 6 capturas + icono + feature graphic`);
}

fs.writeFileSync(OUT_NOTES, `${JSON.stringify(releaseNotes, null, 2)}\n`, 'utf-8');

console.log(`\n📁 Metadata:       ${OUT}`);
console.log(`📁 Solo textos:    ${OUT_TEXT}`);
console.log(`📁 Solo capturas:  ${OUT_SHOTS}`);
console.log(`📄 Release notes:  ${OUT_NOTES}`);
console.log('   gplay metadata validate --dir build/outputs/play-store/metadata');
