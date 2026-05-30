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
      // Object form with singleton:true is mandatory for anything stateful.
      // The array form lets host and remote each load their own copy: two
      // React instances break hooks ("Invalid hook call"), and two react-redux
      // copies give the remote a different Context than its Provider, so
      // useSelector reads an empty store. requiredVersion guards against an
      // incompatible singleton silently winning at runtime.
      shared: {
        react: { singleton: true, requiredVersion: '^18.3.1' },
        'react-dom': { singleton: true, requiredVersion: '^18.3.1' },
        'react-redux': { singleton: true, requiredVersion: '^9.1.2' },
        '@reduxjs/toolkit': { singleton: true, requiredVersion: '^2.2.7' },
      },
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
