import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Load environment variables from the root project directory (.env)
  const env = loadEnv(mode, '../', '');

  const backendPort = env.SERVER_PORT || process.env.SERVER_PORT || '8080';
  const backendUrl = env.BACKEND_URL || process.env.BACKEND_URL || `http://localhost:${backendPort}`;

  // Extract custom trusted hosts from ALLOWED_HOSTS or SITE_ADDRESS without wildcards
  const rawCustomHosts = [
    env.ALLOWED_HOSTS,
    process.env.ALLOWED_HOSTS,
    env.VITE_ALLOWED_HOSTS,
    process.env.VITE_ALLOWED_HOSTS,
    env.SITE_ADDRESS,
    process.env.SITE_ADDRESS,
  ].filter(Boolean).join(',');

  const customAllowedHosts = rawCustomHosts
    .split(',')
    .map(h => h.trim().replace(/^https?:\/\//, '').replace(/\/.*$/, '').replace(/:\d+$/, ''))
    .filter(h => h && h !== ':80' && h !== ':443' && !h.startsWith(':'));

  // Combine trusted hostnames: localhost, .localhost, explicit custom hosts.
  // Note: Numeric IP addresses (localhost, 127.0.0.1, LAN 192.168.*, Tailscale 100.*) are inherently allowed by Vite.
  const allowedHosts = Array.from(new Set(['.localhost', ...customAllowedHosts]));

  return {
    envDir: '../',
    plugins: [react()],
    server: {
      port: 3000,
      host: '0.0.0.0', // Listen on all network addresses for LAN and Tailscale
      allowedHosts: allowedHosts,
      proxy: {
        '/api': {
          target: backendUrl,
          changeOrigin: true,
          secure: false,
          configure: (proxy) => {
            proxy.on('error', (err, req) => {
              console.warn(`[Vite Proxy] Failed to proxy ${req.method} ${req.url} -> ${backendUrl}: ${err.message}`);
            });
          },
        },
      },
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
    },
  };
});


