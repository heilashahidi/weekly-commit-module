import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    // happy-dom keeps fetch/AbortController consistent (jsdom's AbortSignal is
    // incompatible with undici's fetch, which breaks RTK Query fetchBaseQuery).
    environment: 'happy-dom',
    setupFiles: './src/test/setup.ts',
    css: true,
  },
});
