import { describe, expect, it } from 'vitest';
import cases from '../../../packages/contracts/fixtures/domain-cases.json';
import { haversineMeters } from '../src/nearby.js';

// Paridad con Android (H6): haversine no está en @dbus/core, así que el lado TS
// es el bot. El bot no redondea; Android redondea al metro.
describe('haversineMeters (casos compartidos con Android)', () => {
  for (const c of cases.haversineMeters) {
    it(c.name, () => {
      const [lat1, lon1] = c.from;
      const [lat2, lon2] = c.to;
      expect(Math.round(haversineMeters(lat1, lon1, lat2, lon2))).toBe(
        c.expectedMeters
      );
    });
  }
});
