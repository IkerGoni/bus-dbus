#!/usr/bin/env node
/**
 * Genera icono 512×512 y feature graphic 1024×500 para Play Store.
 *
 * Basado en ART-DIRECTION.md:
 * - Paleta: verde petróleo #143E3C, verde pálido #E7F8DF, acento verde menta #A4E8BD, blanco cálido #F8FAF7
 * - Marca: "B" abstracta con señal de llegada/onda
 * - Sin autobús genérico, sin logo Dbus
 *
 * Uso: node 03-gen-assets.mjs
 * Requiere: ImageMagick (magick/convert) instalado
 *
 * Salida: build/outputs/play-store/icon-512.png, feature-graphic-1024x500.png
 */

import fs from 'fs';
import os from 'node:os';
import path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const OUT_DIR = path.join(ROOT, 'build/outputs/play-store');
const PACK = process.env.PLAY_PACK_DIR || path.join(os.homedir(), 'Desktop', 'badator-google-play-pack');

// ---- Paleta ART-DIRECTION ----
const PALETTE = {
  petrolGreen: '#143E3C',    // fondo principal
  paleGreen: '#E7F8DF',      // acento claro
  mintGreen: '#A4E8BD',      // acento medio (onda/punto)
  warmWhite: '#F8FAF7',      // texto/icono sobre fondo oscuro
  darkText: '#1A1A1A',       // texto sobre fondo claro
};

function run(cmd) {
  try {
    return execSync(cmd, { encoding: 'utf-8', maxBuffer: 10 * 1024 * 1024 });
  } catch (e) {
    console.error(`❌ ${cmd}`);
    console.error(e.stdout || e.stderr || e.message);
    throw e;
  }
}

function checkMagick() {
  try {
    run('magick -version');
    return 'magick';
  } catch {
    try {
      run('convert -version');
      return 'convert';
    } catch {
      throw new Error('ImageMagick no instalado. Instala: brew install imagemagick');
    }
  }
}

function checkRsvg() {
  try {
    run('rsvg-convert --version');
    return true;
  } catch {
    return false;
  }
}

// ---- Generar SVG base ----
function generateIconSVG(size) {
  const r = size / 2;
  const center = size / 2;
  const strokeWidth = Math.max(2, size / 100);
  const waveR = r * 0.35;

  return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" xmlns="http://www.w3.org/2000/svg">
  <!-- Fondo circular verde petróleo -->
  <circle cx="${center}" cy="${center}" r="${r - strokeWidth/2}" fill="${PALETTE.petrolGreen}" stroke="none"/>
  
  <!-- "B" abstracta: onda de llegada + punto -->
  <g transform="translate(${center}, ${center})">
    <!-- Onda de llegada (3 líneas curvas) -->
    <g stroke="${PALETTE.mintGreen}" stroke-width="${strokeWidth * 1.5}" fill="none" stroke-linecap="round">
      <path d="M ${-waveR} ${-waveR * 0.3} Q 0 ${-waveR} ${waveR} ${-waveR * 0.3}"/>
      <path d="M ${-waveR * 0.7} ${waveR * 0.1} Q 0 ${-waveR * 0.4} ${waveR * 0.7} ${waveR * 0.1}"/>
      <path d="M ${-waveR * 0.4} ${waveR * 0.5} Q 0 ${waveR * 0.2} ${waveR * 0.4} ${waveR * 0.5}"/>
    </g>
    
    <!-- Punto central (parada/destino) -->
    <circle cx="0" cy="${waveR * 0.5}" r="${strokeWidth * 2.5}" fill="${PALETTE.warmWhite}"/>
  </g>
  
  <!-- Anillo sutil exterior -->
  <circle cx="${center}" cy="${center}" r="${r - strokeWidth * 3}" fill="none" stroke="${PALETTE.mintGreen}" stroke-width="${strokeWidth * 0.5}" opacity="0.3"/>
</svg>`;
}

function generateFeatureGraphicSVG() {
  const w = 1024;
  const h = 500;
  const cx = w / 2;
  const cy = h / 2;

  // Textos
  const taglines = {
    es: 'Tu bus viene. Tú decides cuándo salir.',
    eu: 'Autobusa badator. Jakin noiz iritsiko den.',
    en: 'Know when your bus is coming.',
  };

  return `<svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" xmlns="http://www.w3.org/2000/svg">
  <!-- Fondo verde petróleo con formas suaves -->
  <rect width="${w}" height="${h}" fill="${PALETTE.petrolGreen}"/>
  
  <!-- Formas orgánicas inspiradas en trayectorias urbanas (ondas suaves) -->
  <defs>
    <linearGradient id="waveGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="${PALETTE.mintGreen}" stop-opacity="0.15"/>
      <stop offset="100%" stop-color="${PALETTE.paleGreen}" stop-opacity="0.05"/>
    </linearGradient>
  </defs>
  
  <!-- Ondas de fondo -->
  <path d="M 0 ${h * 0.65} 
           Q ${w * 0.25} ${h * 0.45} ${w * 0.5} ${h * 0.6} 
           Q ${w * 0.75} ${h * 0.75} ${w} ${h * 0.55} 
           L ${w} ${h} L 0 ${h} Z" 
        fill="url(#waveGrad)"/>
  <path d="M 0 ${h * 0.8} 
           Q ${w * 0.3} ${h * 0.65} ${w * 0.55} ${h * 0.75} 
           Q ${w * 0.8} ${h * 0.85} ${w} ${h * 0.7} 
           L ${w} ${h} L 0 ${h} Z" 
        fill="${PALETTE.mintGreen}" opacity="0.08"/>
  
  <!-- Marca Badator! (izquierda) -->
  <g transform="translate(${w * 0.12}, ${cy})">
    <!-- Icono B pequeño -->
    <g transform="translate(-60, -20)">
      <circle cx="20" cy="20" r="20" fill="${PALETTE.mintGreen}" opacity="0.2"/>
      <path d="M 10 10 Q 20 5 30 10 M 10 20 Q 20 15 30 20 M 10 30 Q 20 25 30 30" 
            stroke="${PALETTE.warmWhite}" stroke-width="3" fill="none" stroke-linecap="round"/>
      <circle cx="30" cy="20" r="4" fill="${PALETTE.warmWhite}"/>
    </g>
    <!-- Texto marca -->
    <text x="0" y="-10" font-family="system-ui, sans-serif" font-size="48" font-weight="700" fill="${PALETTE.warmWhite}" letter-spacing="1">Badator!</text>
    <text x="0" y="40" font-family="system-ui, sans-serif" font-size="20" fill="${PALETTE.paleGreen}" letter-spacing="0.5">Bus Donostia</text>
  </g>
  
  <!-- Componente gráfico central/derecha: llegada + notificación -->
  <g transform="translate(${w * 0.68}, ${cy})">
    <!-- Tarjeta de llegada simulada -->
    <rect x="-100" y="-80" width="200" height="160" rx="16" fill="${PALETTE.warmWhite}" opacity="0.95"/>
    
    <!-- Línea de bus -->
    <rect x="-80" y="-60" width="48" height="32" rx="8" fill="${PALETTE.petrolGreen}"/>
    <text x="-56" y="-36" font-family="system-ui, sans-serif" font-size="18" font-weight="700" fill="${PALETTE.warmWhite}" text-anchor="middle">28</text>
    
    <!-- Destino -->
    <text x="0" y="-20" font-family="system-ui, sans-serif" font-size="16" fill="${PALETTE.darkText}" text-anchor="middle">Amara · 3 min</text>
    
    <!-- Punto LIVE pulsante -->
    <circle cx="-20" cy="20" r="6" fill="${PALETTE.mintGreen}">
      <animate attributeName="opacity" values="1;0.3;1" dur="1.5s" repeatCount="indefinite"/>
    </circle>
    <text x="10" y="25" font-family="system-ui, sans-serif" font-size="14" fill="${PALETTE.petrolGreen}" text-anchor="middle">LIVE</text>
    
    <!-- Campana de notificación -->
    <g transform="translate(50, -40)">
      <path d="M 0 -15 Q -10 -15 -10 -5 Q -10 5 0 5 Q 10 5 10 -5 Q 10 -15 0 -15" 
            fill="none" stroke="${PALETTE.petrolGreen}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
      <rect x="-3" y="-15" width="6" height="5" fill="${PALETTE.petrolGreen}"/>
      <!-- Badge de notificación -->
      <circle cx="8" cy="-12" r="8" fill="#E53935"/>
      <text x="8" y="-8" font-family="system-ui, sans-serif" font-size="10" font-weight="700" fill="white" text-anchor="middle">1</text>
    </g>
  </g>
  
  <!-- Tagline ES (centro-abajo, seguro en región central) -->
  <text x="${cx}" y="${h * 0.92}" font-family="system-ui, sans-serif" font-size="24" fill="${PALETTE.paleGreen}" text-anchor="middle" font-weight="500">${taglines.es}</text>
</svg>`;
}

// ---- Main ----
async function main() {
  console.log('🎨 Generando assets gráficos para Play Store...\n');

  const magick = checkMagick();
  const hasRsvg = checkRsvg();
  console.log(`✅ ImageMagick: ${magick}`);
  console.log(`✅ rsvg-convert: ${hasRsvg ? 'sí' : 'no'}`);

  if (!hasRsvg) {
    console.log('⚠️ rsvg-convert no disponible. El feature graphic puede fallar con texto.');
    console.log('   Instala: brew install librsvg');
  }

  fs.mkdirSync(OUT_DIR, { recursive: true });

  // 1. Icono 512×512 (sin texto, usa magick)
  console.log('\n📱 Generando icono 512×512...');
  const iconSVG = generateIconSVG(512);
  const iconSVGPath = path.join(OUT_DIR, 'icon-512.svg');
  fs.writeFileSync(iconSVGPath, iconSVG);
  
  run(`${magick} "${iconSVGPath}" -background none -resize 512x512 "${path.join(OUT_DIR, 'icon-512.png')}"`);
  console.log(`   ✅ ${path.join(OUT_DIR, 'icon-512.png')}`);

  // 2. Feature Graphic 1024×500 (con texto, usa rsvg-convert si disponible)
  console.log('\n🖼️ Generando feature graphic 1024×500...');
  const fgSVG = generateFeatureGraphicSVG();
  const fgSVGPath = path.join(OUT_DIR, 'feature-graphic.svg');
  fs.writeFileSync(fgSVGPath, fgSVG);
  
  if (hasRsvg) {
    run(`rsvg-convert -w 1024 -h 500 -f png -o "${path.join(OUT_DIR, 'feature-graphic-1024x500.png')}" "${fgSVGPath}"`);
  } else {
    run(`${magick} "${fgSVGPath}" -background none -resize 1024x500 "${path.join(OUT_DIR, 'feature-graphic-1024x500.png')}"`);
  }
  console.log(`   ✅ ${path.join(OUT_DIR, 'feature-graphic-1024x500.png')}`);

  // Verificar tamaños
  const iconStat = fs.statSync(path.join(OUT_DIR, 'icon-512.png'));
  const fgStat = fs.statSync(path.join(OUT_DIR, 'feature-graphic-1024x500.png'));
  console.log(`\n📊 Tamaños:`);
  console.log(`   icon-512.png: ${(iconStat.size / 1024).toFixed(1)} KB (máx 1024 KB)`);
  console.log(`   feature-graphic-1024x500.png: ${(fgStat.size / 1024).toFixed(1)} KB`);

  // Copia al pack
  fs.mkdirSync(path.join(PACK, '02-creative'), { recursive: true });
  fs.copyFileSync(path.join(OUT_DIR, 'icon-512.png'), path.join(PACK, '02-creative/icon-512.png'));
  fs.copyFileSync(path.join(OUT_DIR, 'feature-graphic-1024x500.png'), path.join(PACK, '02-creative/feature-graphic-1024x500.png'));
  console.log('\n📋 Copiados a badator-google-play-pack/02-creative/');

  console.log('\n🎉 Assets listos para Play Console!');
  console.log('   - Icono: 512×512 PNG 32-bit con alfa');
  console.log('   - Feature graphic: 1024×500 PNG 24-bit');
}

main().catch(console.error);