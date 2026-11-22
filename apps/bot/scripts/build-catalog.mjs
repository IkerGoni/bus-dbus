#!/usr/bin/env node
/**
 * Genera bot/data/dbus-catalog.json desde la API JSON real de Dbus.
 *
 * El catálogo es solo un ÍNDICE DE DESCUBRIMIENTO (buscar paradas por nombre,
 * líneas por código, destinos). Las llegadas SIEMPRE se consultan en vivo.
 *
 * Rango de líneas: la API NO expone un endpoint de listado de líneas. Se
 * comprobó (2026-11-22) que `/lineas`, `/listaLineas`, `/getLineas`,
 * `/paradasLinea`, `/itinerarios`, `/empresas` y variantes devuelven 404; y que
 * `/lineasParada` existe pero responden 400 de Tomcat para toda combinación de
 * parámetros probada (GET/POST, codParada/idParada/id...). Tampoco hay WSDL.
 * Por tanto el rango se DESCUBRE sondeando `/itinerariosLinea?idLinea=N`:
 * se recorren TODOS los ids de 1 a HARD_CAP (1000) en bloques, sin parar en
 * huecos de 30 vacíos (un bloque temporal sin datos recortaba el catálogo).
 * Los ids 121..300 están vacíos hoy, pero el día que Dbus añada líneas el
 * escaneo las encontrará sin tocar el código. HARD_CAP es configurable con
 * DBUS_MAX_LINE_ID; el rango real se guarda en `coverage`.
 *
 * Uso: node scripts/build-catalog.mjs
 *      DBUS_BASE_URL=... node scripts/build-catalog.mjs
 */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE =
  process.env.DBUS_BASE_URL || 'http://app.dbus.eus/SSIIMovilWSv2/ws/cons';
const CONCURRENCY = 6;
const FIRST_LINE_ID = 1;
const BLOCK = 10;
// Techo del sondeo: se recorre entero para no recortar ante huecos temporales.
const HARD_CAP = Number.parseInt(process.env.DBUS_MAX_LINE_ID ?? '1000', 10);
if (!Number.isInteger(HARD_CAP) || HARD_CAP < 120 || HARD_CAP > 5000) {
  throw new Error('[catalog] DBUS_MAX_LINE_ID debe estar entre 120 y 5000');
}

const dir = path.dirname(fileURLToPath(import.meta.url));
const outFile = path.resolve(dir, '../data/dbus-catalog.json');

async function getJson(pathname) {
  const url = `${BASE}${pathname}`;
  let lastError;
  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      const response = await fetch(url, {
        headers: { Accept: 'application/json' },
        signal: AbortSignal.timeout(10_000),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return await response.json();
    } catch (error) {
      lastError = error;
    }
  }
  // Un id sin respuesta no se salta en silencio: eso dejaría un catálogo
  // incompleto que parecería válido.
  throw new Error(`[catalog] ${pathname}: ${String(lastError)}`);
}

async function mapLimit(items, limit, fn) {
  const results = [];
  let cursor = 0;
  const workers = Array.from(
    { length: Math.min(limit, items.length) },
    async () => {
      while (cursor < items.length) {
        const index = cursor++;
        results[index] = await fn(items[index]);
      }
    }
  );
  await Promise.all(workers);
  return results;
}

/** Descubre el rango de ids y devuelve todas las respuestas de línea. */
async function discoverLines() {
  const responses = new Map();
  let lastWithData = 0;
  let scanned = FIRST_LINE_ID - 1;

  while (scanned < HARD_CAP) {
    const block = Array.from(
      { length: Math.min(BLOCK, HARD_CAP - scanned) },
      (_, i) => String(scanned + i + 1)
    );
    const batch = await mapLimit(block, CONCURRENCY, (id) =>
      getJson(`/itinerariosLinea?idLinea=${id}`)
    );
    block.forEach((id, i) => {
      responses.set(id, batch[i]);
      const raw = batch[i];
      if (
        raw?.estado === 'DATOS' &&
        Array.isArray(raw.lista) &&
        raw.lista.length > 0
      ) {
        lastWithData = Math.max(lastWithData, Number(id));
      }
    });
    scanned += BLOCK;
  }

  return { responses: [...responses.values()], scanned, lastWithData };
}

const discovered = await discoverLines();
const lineResponses = discovered.responses;

const lines = [];
const itineraries = [];
for (const raw of lineResponses) {
  if (!raw || raw.estado !== 'DATOS' || !Array.isArray(raw.lista)) continue;
  const id = String(raw.linea?.id ?? '');
  const cod = String(raw.linea?.cod ?? '');
  if (!id || !cod) continue;
  lines.push({ id, cod });
  for (const itinerario of raw.lista) {
    itineraries.push({
      id: String(itinerario.id),
      lineId: id,
      lineCod: cod,
      destination: String(itinerario.destino || ''),
    });
  }
}

const stopResponses = await mapLimit(itineraries, CONCURRENCY, (itinerario) =>
  getJson(`/paradasItinerario?idItinerario=${itinerario.id}`)
);

const stopsByCod = new Map();
for (let i = 0; i < itineraries.length; i++) {
  const itinerario = itineraries[i];
  const raw = stopResponses[i];
  itinerario.stops = [];
  if (!raw || !Array.isArray(raw.lista)) continue;
  for (const item of raw.lista) {
    const cod = String(item?.cod ?? '');
    const desc = String(item?.desc ?? '').trim();
    if (!cod || !desc) continue;
    itinerario.stops.push(cod);
    const existing = stopsByCod.get(cod);
    if (existing) {
      if (!existing.lines.includes(itinerario.lineCod)) {
        existing.lines.push(itinerario.lineCod);
      }
      if (!existing.destinations.includes(itinerario.destination)) {
        existing.destinations.push(itinerario.destination);
      }
    } else {
      stopsByCod.set(cod, {
        cod,
        desc,
        lines: [itinerario.lineCod],
        destinations: [itinerario.destination],
      });
    }
  }
}

for (const line of lines) {
  line.destinations = itineraries
    .filter((itinerario) => itinerario.lineId === line.id)
    .map((itinerario) => itinerario.destination)
    .filter(Boolean);
  line.stops = [
    ...new Set(
      itineraries
        .filter((itinerario) => itinerario.lineId === line.id)
        .flatMap((itinerario) => itinerario.stops)
    ),
  ];
}

const catalog = {
  generatedAt: new Date().toISOString(),
  source: BASE,
  attribution: 'Datos de paradas: Dbus Donostia (API pública SSIIMovilWSv2)',
  coverage: {
    lineIdsScanned: discovered.scanned,
    lastLineIdWithData: discovered.lastWithData,
    emptyTail: discovered.scanned - discovered.lastWithData,
  },
  lines,
  itineraries,
  stops: [...stopsByCod.values()].sort((a, b) =>
    a.cod.localeCompare(b.cod, 'es', { numeric: true })
  ),
};

// Una API degradada que devuelve "NO ITINERARIOS" para todo no debe
// sobrescribir un índice bueno con un catálogo vacío.
if (lines.length === 0 || stopsByCod.size === 0) {
  throw new Error(
    '[catalog] sin líneas o paradas en la respuesta; se conserva el índice anterior'
  );
}

await fs.mkdir(path.dirname(outFile), { recursive: true });
await fs.writeFile(outFile, `${JSON.stringify(catalog, null, 2)}\n`);
console.log(
  `[catalog] ${lines.length} líneas · ${itineraries.length} itinerarios · ${stopsByCod.size} paradas · ids 1..${discovered.scanned} → ${outFile}`
);
