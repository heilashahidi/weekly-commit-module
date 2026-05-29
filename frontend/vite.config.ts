import federation from '@originjs/vite-plugin-federation';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Runs standalone AND is structured as a Module Federation remote: a single
// exposed entry, shared React singletons. The host (PA) loads `remoteEntry.js`.
export default defineConfig({
  plugins: [
    react(),
    federation({
      name: 'weeklyCommit',
      filename: 'remoteEntry.js',
      exposes: {
        './WeeklyCommitApp': './src/WeeklyCommitApp.tsx',
      },
      shared: ['react', 'react-dom'],
    }),
  ],
  server: { port: 5173 },
  preview: { port: 4173 },
  // Module Federation needs a modern target (top-level await in the runtime).
  build: {
    target: 'esnext',
    minify: false,
    cssCodeSplit: false,
  },
});
