import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The console talks to the gateway through these proxies in dev, so there's no CORS
// to configure: the browser only ever sees one origin (the Vite dev server). In
// production the same paths are proxied by nginx to the gateway container.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // 127.0.0.1 (not localhost) so Node doesn't resolve to IPv6 ::1 where the gateway isn't listening.
      '/v1': 'http://127.0.0.1:8080',
      '/auth': 'http://127.0.0.1:8080',
    },
  },
})
