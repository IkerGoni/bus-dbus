#!/usr/bin/env node
/**
 * Genera bot/data/dbus-catalog.json desde la API JSON real de Dbus.
 *
 * El catálogo es solo un ÍNDICE DE DESCUBRIMIENTO (buscar paradas por nombre,
 * líneas por código, destinos). Las llegadas SIEMPRE se consultan en vivo.
 *
 * Uso: node scripts/build-catalog.mjs
 *      DBUS_BASE_URL=... node scripts/build-catalog.mjs
 */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE =
  process.env.DBUS_BASE_URL || 'http://app.dbus.eus/SSIIMovilWSv2/ws/cons';
const MAX_LINE_ID = 120; // los ids reales detectados llegan hasta 88
const CONCURRENCY = 6;

const dir = path.dirname(fileURLToPath(import.meta.url));
const outFile = path.resolve(dir, '../data/dbus-catalog.json');

async function getJson(pathname) {
  const url = `${BASE}${pathname}`;
  for (let attempt = 1; attempt <= 2; attempt++) {
    try {
      const response = await fetch(url, {
        headers: { Accept: 'application/json' },
        signal: AbortSignal.timeout(10_000),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return await response.json();
    } catch (error) {
      if (attempt === 2) {
        console.error(`[catalog] ${pathname}: ${String(error)}`);
        return null;
      }
    }
  }
  return null;
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

const ids = Array.from({ length: MAX_LINE_ID }, (_, i) => String(i + 1));
const lineResponses = await mapLimit(ids, CONCURRENCY, (id) =>
  getJson(`/itinerariosLinea?idLinea=${id}`)
);

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
  lines,
  itineraries,
  stops: [...stopsByCod.values()].sort((a, b) =>
    a.cod.localeCompare(b.cod, 'es', { numeric: true })
  ),
};

await fs.mkdir(path.dirname(outFile), { recursive: true });
await fs.writeFile(outFile, `${JSON.stringify(catalog, null, 2)}\n`);
console.log(
  `[catalog] ${lines.length} líneas · ${itineraries.length} itinerarios · ${stopsByCod.size} paradas → ${outFile}`
);
