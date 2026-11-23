#!/usr/bin/env node
/**
 * Verifica y sugiere correcciones para el listing EU (euskera).
 *
 * Compara:
 * - 01-listing/eu-ES.md (pack) vs strings-eu/strings.xml (app real)
 * - Detecta literalismos, inconsistencias, términos no nativos
 *
 * Uso: node 06-verify-eu.mjs
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const PACK = '/Users/dev/Desktop/badator-google-play-pack';

// ---- Diccionario de correcciones comunes (literalismos → nativo) ----
const CORRECTIONS = {
  // Verbos / acciones
  'kontsultatu': 'ikusi',           // "consultar" → "ver/mira" (más natural)
  'ezarri': 'jarri',                // "configurar/establecer" → "poner" (coloquial)
  'gorde': 'babestu',               // "guardar" → "babestu" (más euskaldun)
  'bilatu': 'aurkitu',              // "buscar" → "encontrar" (acción completada)
  'aukeratu': 'hautatu',            // "seleccionar/optar" → "elegir"
  'konfiguratu': 'ezarri',          // "configurar" (castellanismo)
  'personalizatu': 'egokitu',       // "personalizar" → "adaptar"
  'notifikatu': 'abisatu',          // "notificar" → "avisar"
  'desaktibatu': 'eten',            // "desactivar" → "parar/quitar"

  // Sustantivos
  'abisua': 'abisua',               // OK
  'abisuak': 'abisuak',             // OK
  'geltokia': 'geltokia',           // OK
  'geltokiak': 'geltokiak',         // OK
  'linea': 'linea',                 // OK (préstamo aceptado)
  'lineak': 'lineak',               // OK
  'helmuga': 'helmuga',             // OK
  'helmugak': 'helmugak',           // OK
  'iritsiera': 'iritsiera',         // OK
  'iritsierak': 'iritsierak',       // OK
  'ordutegia': 'ordutegia',         // "horario" → OK
  'estimazioa': 'aurreikuntza',    // "estimación" → "previsión/anticipación"
  'antelazioa': 'aurrerapen',      // "antelación" → "adelanto/anticipación"
  'favoritoa': 'gogokoa',           // "favorito" → "gusto/preferido"
  'gogokoak': 'gogokoak',           // OK
  'aurrerapen': 'aurrerapen',       // OK
  'distantzia': 'distantzia',       // OK
  'minutu': 'minutu',               // OK
  'segundua': 'segundua',           // OK (evitar en UI)

  // Adjetivos / calificativos
  'ofiziala': 'ofiziala',           // OK
  'ez-ofiziala': 'ez-ofiziala',     // OK
  'ofizial': 'ofiziala',            // corregir
  'ez-ofizial': 'ez-ofiziala',      // corregir
  'azar': 'azar',                   // OK (azar/aleatorio)
  'etengabe': 'etengabe',           // OK (continuo)
  'berehala': 'berehala',           // OK (inmediato)
  'ezinbestekoa': 'ezinbestekoa',   // "necesario" → "imprescindible"
  'zuzenean': 'zuzenean',           // OK (en directo/vivo)
  'benetako': 'benetako',           // "real" → OK
  'eguneratua': 'eguneratua',       // "actualizado" → OK

  // Expresiones / perífrasis
  'ez da... 머리카락': 'ez da... hair', // placeholder
  'beharrezkoa da': 'behar da',     // "es necesario" → "hace falta"
  'egin behar da': 'egina behar da', // "hay que hacer" → OK
  'aurki dezakezu': 'aurkituko duzu', // "puedes encontrar" → "encontrarás"
  'ezarri dezakezu': 'jarri dezakezu', // "puedes configurar" → "puedes poner"
  'kontsultatzen duzu': 'ikusten duzu', // "consultas" → "ves/miras"
  'gordetzen duzu': 'babestetzen duzu', // "guardas" → "cuidas/guardas (babestu)"
  'bilatzen duzu': 'aurkitzen duzu', // "buscas" → "encuentras"

  // UI / términos técnicos
  'pantaila': 'pantaila',           // OK
  'botoia': 'botoia',               // OK (botón)
  'menu': 'menua',                  // corregir
  'ezarpenak': 'ezarpenak',         // OK (ajustes)
  'abisua ezarri': 'abisua jarri',  // "configurar aviso" → "poner aviso"
  'gorde bila': 'babestu bila',     // "guardar búsqueda" → "guardar búsqueda (babestu)"

  // Frases completas (castellanismos frecuentes)
  'no oficial': 'ez-ofiziala',
  'sin anuncios': 'anuntzarik gabe',
  'gratis': 'doan',
  'gratuito': 'doan',
  'versión': 'bertzioa',
  'actualización': 'eguneraketa',
  'permiso': 'baimena',
  'notificación': 'abisua',
  'alerta': 'abisua',
  'favoritos': 'gogokoak',
  'recientes': 'azkenak',
  'cercanas': 'hurbilekoak',
  'próximas': 'hurrengoak',
  'llegadas': 'iritsierak',
  'salidas': 'irteerak', // no usar
  'horario': 'ordutegia',
  'tiempo real': 'denbora erreala',
  'en vivo': 'zuzenean',
  'directo': 'zuzenean',
  'mapa': 'mapa',
  'navegación': 'nabigazioa',
  'transporte': 'trasporta',
  'autobús': 'autobusa',
  'bus': 'bus', // préstamo aceptado
  'parada': 'geltokia',
  'marquesina': 'markezina', // préstamo
  'andén': 'andena', // no usar
  'línea': 'linea',
  'ruta': 'ruta', // préstamo
  'destino': 'helmuga',
  'origen': 'jatorria',
  'antelación': 'aurrerapen',
  'minutos': 'minutuak',
  'segundos': 'segunduak', // evitar
  'ahora': 'orain',
  'pronto': 'laster',
  'tarde': 'etzi',
  'temprano': 'goizean',
  'mañana': 'bihar',
  'hoy': 'gaur',
  'ayer': 'atzo',
  'semana': 'astea',
  'mes': 'hila',
  'año': 'urtea',
};

function loadListing() {
  const listingPath = path.join('/Users/dev/Desktop/badator-google-play-pack/01-listing/eu-ES.md');
  return fs.readFileSync(listingPath, 'utf-8');
}

function loadStringsEU() {
  const stringsPath = path.join(__dirname, '../../apps/android/app/src/main/res/values-eu/strings.xml');
  return fs.readFileSync(stringsPath, 'utf-8');
}

function loadStringsEUSettings() {
  const stringsPath = path.join(__dirname, '../../apps/android/app/src/main/res/values-eu/strings_settings.xml');
  return fs.readFileSync(stringsPath, 'utf-8');
}

function loadStringsEUEngine() {
  const stringsPath = path.join(__dirname, '../../apps/android/app/src/main/res/values-eu/strings_engine.xml');
  return fs.readFileSync(stringsPath, 'utf-8');
}

function extractStrings(xml) {
  const strings = {};
  const regex = /<string name="([^"]+)">([^<]*)<\/string>/g;
  let match;
  while ((match = regex.exec(xml))) {
    strings[match[1]] = match[2];
  }
  const pluralRegex = /<plurals name="([^"]+)">([\s\S]*?)<\/plurals>/g;
  while ((match = pluralRegex.exec(xml))) {
    const name = match[1];
    const content = match[2];
    const itemRegex = /<item quantity="([^"]+)">([^<]*)<\/item>/g;
    let itemMatch;
    while ((itemMatch = itemRegex.exec(content))) {
      strings[`${name}[${itemMatch[1]}]`] = itemMatch[2];
    }
  }
  return strings;
}

function checkText(text, source) {
  const issues = [];
  const words = text.toLowerCase().split(/[\s\.,;:¡!¿?()\[\]{}"'-]+/);
  
  for (const word of words) {
    if (word.length < 3) continue;
    if (CORRECTIONS[word] && CORRECTIONS[word] !== word) {
      issues.push({ original: word, suggestion: CORRECTIONS[word], context: source });
    }
  }
  return issues;
}

function main() {
  console.log('🔍 Verificando listing EU (euskera)...\n');

  const listing = loadListing();
  const stringsEU = loadStringsEU();
  const stringsEUSettings = loadStringsEUSettings();
  const stringsEUEngine = loadStringsEUEngine();

  const allStrings = {
    ...extractStrings(stringsEU),
    ...extractStrings(stringsEUSettings),
    ...extractStrings(stringsEUEngine),
  };

  console.log('📋 Listing (pack):');
  const listingIssues = checkText(listing, 'listing');
  if (listingIssues.length > 0) {
    listingIssues.forEach(i => console.log(`  ⚠️ "${i.original}" → "${i.suggestion}" (en ${i.context})`));
  } else {
    console.log('  ✅ Sin literalismos detectados en listing');
  }

  console.log('\n📱 Strings app (values-eu):');
  let totalIssues = 0;
  for (const [key, value] of Object.entries(allStrings)) {
    const issues = checkText(value, `strings.xml:${key}`);
    if (issues.length > 0) {
      totalIssues += issues.length;
      issues.forEach(i => console.log(`  ⚠️ [${key}] "${i.original}" → "${i.suggestion}"`));
    }
  }
  if (totalIssues === 0) {
    console.log('  ✅ Sin literalismos detectados en strings');
  }

  // Verificar paridad ES/EU
  console.log('\n⚖️ Verificando paridad ES/EU (keys):');
  const stringsES = fs.readFileSync(path.join(__dirname, '../../apps/android/app/src/main/res/values/strings.xml'), 'utf-8');
  const stringsESs = extractStrings(stringsES);
  const keysES = new Set(Object.keys(stringsESs));
  const keysEU = new Set(Object.keys(allStrings));
  
  const missingInEU = [...keysES].filter(k => !keysEU.has(k));
  const extraInEU = [...keysEU].filter(k => !keysES.has(k));
  
  if (missingInEU.length > 0) {
    console.log(`  ❌ Faltan en EU: ${missingInEU.join(', ')}`);
  } else {
    console.log('  ✅ Todas las keys ES tienen equivalente en EU');
  }
  if (extraInEU.length > 0) {
    console.log(`  ⚠️ Extras en EU (no en ES): ${extraInEU.join(', ')}`);
  }

  // Verificar listing vs strings app
  console.log('\n🔗 Listing vs Strings app:');
  const listingKeys = ['app_launcher_name', 'app_name', 'nav_home', 'nav_stops', 'nav_lines', 'nav_alerts'];
  for (const key of listingKeys) {
    if (allStrings[key]) {
      console.log(`  ✅ ${key}: "${allStrings[key]}"`);
    } else {
      console.log(`  ❌ Falta key en strings: ${key}`);
    }
  }

  console.log('\n📝 RESUMEN:');
  console.log(`  - Issues en listing: ${listingIssues.length}`);
  console.log(`  - Issues en strings: ${totalIssues}`);
  console.log(`  - Keys faltantes en EU: ${missingInEU.length}`);
  
  if (listingIssues.length > 0 || totalIssues > 0 || missingInEU.length > 0) {
    console.log('\n⚠️ REVISIÓN NECESARIA por euskaldun nativo antes de publicar.');
  } else {
    console.log('\n✅ Todo OK para revisión nativa.');
  }
}

main().catch(console.error);