/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const envAllowedHosts = (process.env.ALLOWED_HOSTS || process.env.VITE_ALLOWED_HOSTS || '')
  .split(',')
  .map(h => h.trim())
  .filter(Boolean);

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    host: true, // Listen on all network addresses (LAN access)
    // Vite automatically allows numeric IP addresses (127.0.0.1, LAN 192.168.*, Tailscale 100.*).
    // Explicitly trust .localhost, Tailscale MagicDNS (.ts.net), and custom env hostnames while rejecting untrusted hostnames.
    allowedHosts: ['.localhost', '.ts.net', ...envAllowedHosts],
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

