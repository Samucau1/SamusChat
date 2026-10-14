import { defineConfig } from 'vite';
const target = process.env.SAMUSCHAT_API_URL || 'http://127.0.0.1:8080';
export default defineConfig({ server: { proxy: {
  '/api': { target, changeOrigin: true },
  '/uploads': { target, changeOrigin: true },
  '/ws': { target, ws: true, changeOrigin: true },
} } });
