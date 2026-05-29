import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './index.css';

// U7 replaces this with delegation to an async bootstrap so the standalone and
// Module Federation remote entry points share one code path.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
