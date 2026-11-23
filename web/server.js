// node server.js — o FreeVideo no computador: as páginas e a ponte com o YouTube.
// Variáveis: PORT (3992), HOST (127.0.0.1). Ver web/README.md.
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createWeb } from './server/app.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const port = parseInt(process.env.PORT ?? '3992', 10);
const host = process.env.HOST ?? '127.0.0.1';
const web = createWeb({ publicDir: path.join(here, 'public') });
web.server.listen(port, host, () => console.log(`[web] FreeVideo web em http://${host}:${port}/`));
const stop = () => {
  setTimeout(() => process.exit(0), 3000).unref();
  web.server.close(() => process.exit(0));
  web.server.closeIdleConnections?.();
};
process.on('SIGTERM', stop);
process.on('SIGINT', stop);
