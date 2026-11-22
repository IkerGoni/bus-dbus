#!/usr/bin/env node
import dns from 'node:dns';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Genera data/dbus-stop-coordinates.json desde OpenStreetMap (Overpass).
 *
 * Índice LOCAL de coordenadas: se ejecuta UNA VEZ (o cuando haga falta
 * refrescar) y el bot lo consume sin depender de Overpass en caliente.
 * Read-only: solo GET/POST al endpoint de Overpass, nunca a la API de Dbus.
 *
 * Cruce: un elemento OSM (node/way con highway=bus_stop o
 * public_transport=platform y tag `ref`) cuenta solo si su ref numérico
 * existe en dbus-catalog.json. No se inventan paradas ni se rellena huecos.
 * Cuando un mismo código aparece en varios elementos (ref compartidos con
 * otros operadores, dobles andenes, refs tipo "202 (dbus);260 (Lurraldebus)")
 * se elige el candidato con mejor evidencia: ref etiquetado como dbus >
 * operator/network DBUS > similitud de nombre con el catálogo > node sobre
 * way. Se descartan refs etiquetados exclusivamente a otro operador y los
 * números pegados a letras (p. ej. "BU12" no es el código 12).
 *
 * Uso: node scripts/build-stop-coordinates.mjs
 *      OVERPASS_BBOX=... node scripts/build-stop-coordinates.mjs
 */

// overpass-api.de resolvía primero IPv6 y colgaba en nuestro entorno.
dns.setDefaultResultOrder('ipv4first');

const dir = path.dirname(fileURLToPath(import.meta.url));
const catalogFile = path.resolve(dir, '../data/dbus-catalog.json');
const outFile = path.resolve(dir, '../data/dbus-stop-coordinates.json');

// sur, oeste, norte, este — Donostia y su área metropolitana.
const BBOX = process.env.OVERPASS_BBOX ?? '43.24,-2.08,43.39,-1.88';
const ENDPOINTS = [
  'https://overpass-api.de/api/interpreter',
  'https://overpass.private.coffee/api/interpreter',
  'https://overpass.kumi.systems/api/interpreter',
  'https://overpass.nchc.org.tw/api/interpreter',
];
const ATTEMPTS_PER_ENDPOINT = Number.parseInt(
  process.env.OVERPASS_ATTEMPTS ?? '2',
  10
);
const TIMEOUT_MS = Number.parseInt(
  process.env.OVERPASS_TIMEOUT_MS ?? '120000',
  10
);
const QUERY = `[out:json][timeout:180];
(
  node["highway"="bus_stop"]["ref"](${BBOX});
  node["public_transport"="platform"]["ref"](${BBOX});
  way["highway"="bus_stop"]["ref"](${BBOX});
  way["public_transport"="platform"]["ref"](${BBOX});
);
out center;`;

const OTHER_OPERATORS =
  /lurralde|tbh|euskotren|adif|ekialde|iren|boba|tcc|renfe|feve/i;
const STOP_WORDS = new Set(['el', 'la', 'de', 'del', 'los', 'las']);

function normalize(value) {
  return String(value ?? '')
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

/** Afinidad entre el nombre del catálogo y el `name` de OSM (0..1). */
function nameAffinity(catalogDesc, osmName) {
  const a = normalize(catalogDesc);
  const b = normalize(osmName);
  if (!a || !b) return 0;
  if (a === b) return 1;
  if (a.includes(b) || b.includes(a)) return 0.9;
  const tokensA = new Set(a.split(' '));
  const tokensB = new Set(b.split(' '));
  const shared = [...tokensA].filter(
    (token) => !STOP_WORDS.has(token) && tokensB.has(token)
  ).length;
  return shared / Math.min(tokensA.size, tokensB.size);
}

/**
 * Extrae códigos de un `ref`. Solo números independientes: "BU12" no es 12.
 * Anotaciones tipo "(dbus)" / "(Lurraldebus)" se usan para preferir/descartar.
 */
function parseRefNumbers(raw) {
  const refs = [];
  for (const segment of String(raw ?? '').split(/[;,/]/)) {
    const numbers = segment.match(/(?<![A-Za-z])\d+(?![A-Za-z])/g) ?? [];
    if (numbers.length === 0) continue;
    const label = normalize(segment.replace(/\d+/g, ' '));
    const dbus = /\bdbus\b/.test(label);
    const other = !dbus && OTHER_OPERATORS.test(label);
    for (const number of numbers) {
      refs.push({ cod: number.replace(/^0+(?=\d)/, ''), dbus, other });
    }
  }
  return refs;
}

function elementPoint(element) {
  const point =
    element.lat !== undefined && element.lon !== undefined
      ? { lat: element.lat, lon: element.lon }
      : element.center;
  if (!point || !Number.isFinite(point.lat) || !Number.isFinite(point.lon)) {
    return null;
  }
  return point;
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function queryOverpass() {
  const errors = [];
  let attempt = 0;
  for (const endpoint of ENDPOINTS) {
    for (let i = 0; i < ATTEMPTS_PER_ENDPOINT; i++) {
      attempt++;
      try {
        const response = await fetch(endpoint, {
          method: 'POST',
          headers: {
            Accept: 'application/json',
            'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
            'User-Agent': 'dbus-stop-coordinates-index/1.0 (read-only)',
          },
          body: new URLSearchParams({ data: QUERY }),
          signal: AbortSignal.timeout(TIMEOUT_MS),
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const body = await response.json();
        if (!Array.isArray(body?.elements)) {
          throw new Error('respuesta sin elements');
        }
        console.log(
          `[coords] Overpass OK: ${endpoint} (${body.elements.length} elementos)`
        );
        return body.elements;
      } catch (error) {
        const reason = error instanceof Error ? error.message : String(error);
        errors.push(`${endpoint}: ${reason}`);
        console.warn(`[coords] ${endpoint}: ${reason}`);
        if (attempt < ENDPOINTS.length * ATTEMPTS_PER_ENDPOINT) {
          await sleep(Math.min(8000, 1000 * 2 ** (attempt - 1)));
        }
      }
    }
  }
  throw new Error(`[coords] Overpass no respondió:\n  ${errors.join('\n  ')}`);
}

const catalog = JSON.parse(await fs.readFile(catalogFile, 'utf8'));
const stops = Array.isArray(catalog?.stops) ? catalog.stops : [];
if (stops.length === 0) {
  throw new Error('[coords] catálogo vacío; se conserva el índice anterior');
}
const byCod = new Map(stops.map((stop) => [String(stop.cod), stop]));

const elements = await queryOverpass();
const candidates = new Map();
for (const element of elements) {
  const point = elementPoint(element);
  if (!point) continue;
  const refs = parseRefNumbers(element.tags?.ref);
  if (refs.length === 0) continue;
  const operator = normalize(
    `${element.tags?.operator ?? ''} ${element.tags?.network ?? ''}`
  );
  const hasDbus = /\bdbus\b/.test(operator);
  const hasOther = !hasDbus && OTHER_OPERATORS.test(operator);

  for (const ref of refs) {
    if (!byCod.has(ref.cod)) continue;
    if (ref.other && !ref.dbus) continue;
    const score =
      (ref.dbus ? 100 : 0) +
      (hasDbus ? 50 : 0) -
      (hasOther ? 50 : 0) +
      nameAffinity(byCod.get(ref.cod).desc, element.tags?.name) * 60 +
      (element.type === 'node' ? 10 : 0);
    if (score < 0) continue; // operador ajeno sin evidencia de Dbus
    if (!candidates.has(ref.cod)) candidates.set(ref.cod, []);
    candidates.get(ref.cod).push({
      score,
      id: Number(element.id) || 0,
      lat: point.lat,
      lon: point.lon,
    });
  }
}

const indexed = [];
for (const [cod, options] of candidates) {
  options.sort((a, b) => b.score - a.score || a.id - b.id);
  const best = options[0];
  indexed.push({ cod, lat: best.lat, lon: best.lon });
}
indexed.sort((a, b) => a.cod.localeCompare(b.cod, 'es', { numeric: true }));

if (indexed.length === 0) {
  throw new Error('[coords] cobertura 0; no se escribe un JSON vacío');
}

const output = {
  generatedAt: new Date().toISOString(),
  source: 'OpenStreetMap (Overpass)',
  attribution: '© OpenStreetMap contributors, ODbL',
  coverage: {
    catalogStops: stops.length,
    withCoordinates: indexed.length,
    withoutCoordinates: stops.length - indexed.length,
  },
  stops: indexed,
};

await fs.mkdir(path.dirname(outFile), { recursive: true });
await fs.writeFile(outFile, `${JSON.stringify(output, null, 2)}\n`);
console.log(
  `[coords] ${output.coverage.withCoordinates}/${output.coverage.catalogStops} paradas con coordenadas → ${outFile}`
);
