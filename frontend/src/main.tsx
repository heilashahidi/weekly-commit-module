// Async boundary: dynamically importing the bootstrap lets Module Federation
// initialize shared-dependency negotiation before the app mounts. The standalone
// and remote entry points therefore run the same code path.
import('./bootstrap');
