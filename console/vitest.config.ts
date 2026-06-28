import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Test runner config for the console. Keeps the same React plugin the app build uses, runs tests in
// a jsdom DOM, and registers a setup file that wires up @testing-library/jest-dom matchers plus a
// couple of jsdom polyfills (matchMedia / ResizeObserver) the UI primitives expect.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.{test,spec}.{ts,tsx}', 'src/test/**', 'src/main.tsx'],
    },
  },
})
