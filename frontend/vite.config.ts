import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Module Federation remote config is layered on in U7.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173 },
  preview: { port: 4173 },
});
