import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';

const offlinePayload = JSON.stringify({
  ok: false,
  success: false,
  mongo: false,
  error: 'API server band hai. Server folder me node .\\index.js chalao.',
  sims: [],
  devices: [],
  data: [],
  messageCount: 0,
  callCount: 0,
});

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:3000',
        changeOrigin: true,
        configure(proxy) {
          proxy.on('error', (_err, _req, res) => {
            if (res && !res.headersSent && typeof res.writeHead === 'function') {
              res.writeHead(200, {'Content-Type': 'application/json'});
              res.end(offlinePayload);
            }
          });
        },
      },
    },
  },
});
