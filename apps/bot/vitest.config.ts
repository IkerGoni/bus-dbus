import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    setupFiles: ['./vitest.setup.ts'],
    environment: 'node',
    include: ['tests/**/*.test.ts'],
    globals: true,
    // Los mocks de db.js en setupFiles necesitan que cada test importe
    // el módulo después del setup. vi.resetModules por archivo evita caché.
    isolate: true,
    server: {
      deps: {
        inline: ['../src/db.js'],
      },
    },
  },
});
