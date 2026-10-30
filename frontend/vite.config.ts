/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    host: true, // Listen on all network addresses (LAN access)
    allowedHosts: true, // Allow Tailscale IPs (100.x.y.z) and MagicDNS (*.ts.net)
    proxy: {
      '/api': {
        target: process.env.BACKEND_URL || (process.env.SERVER_PORT ? `http://localhost:${process.env.SERVER_PORT}` : 'http://localhost:8080'),
        changeOrigin: true,
        secure: false,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
  },
})

