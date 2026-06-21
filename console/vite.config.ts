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
      '/v1': 'http://localhost:8080',
      '/auth': 'http://localhost:8080',
    },
  },
})
