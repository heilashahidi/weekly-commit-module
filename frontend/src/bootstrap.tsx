import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import WeeklyCommitApp from './WeeklyCommitApp';
import './index.css';

// Standalone mount. Reached via the async import in main.tsx so shared-dependency
// negotiation works identically whether running standalone or as a remote.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <WeeklyCommitApp />
  </StrictMode>,
);
