import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev proxy splits /api between the two backend services, so the browser only
// ever talks to one origin (no CORS in dev, and the same paths work in docker).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api/auth': { target: 'http://localhost:8082', changeOrigin: true },
      '/api/bookings': { target: 'http://localhost:8082', changeOrigin: true },
      '/api/events': { target: 'http://localhost:8081', changeOrigin: true },
    },
  },
});
