import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import fs from 'fs';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  // Load env file based on `mode` in the current working directory.
  const env = loadEnv(mode, process.cwd(), '');

  // Get API Gateway URL from env with fallback
  const apiGatewayUrl = env.VITE_API_GATEWAY_URL || 'https://localhost:7777';
  console.log('API Gateway URL:', apiGatewayUrl);

  // HTTPS configuration based on environment
  const httpsConfig = mode === 'production'
    ? false  // In production, SSL is handled by load balancer/reverse proxy
    : {
      key: fs.readFileSync(path.resolve(__dirname, '../keys/edge-private.key')),
      cert: fs.readFileSync(path.resolve(__dirname, '../keys/edge-certificate.crt')),
    };

  return {
    plugins: [react()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: {
      port: 3000,
      strictPort: true,
      cors: true,
      open: true,
      https: httpsConfig
    },
    build: {
      outDir: 'dist',
      sourcemap: true,
      rollupOptions: {
        input: {
          main: path.resolve(__dirname, 'index.html'),
        },
      },
      // Prevent bundling of certain imported packages
      commonjsOptions: {
        include: [/node_modules/],
      },
    },

    optimizeDeps: {
      include: ['react', 'react-dom'],
    },
    css: {
      // CSS preprocessing
      preprocessorOptions: {
        scss: {
          additionalData: `@import "@/assets/styles/variables.scss";`
        }
      }
    },
    // Environment variables that should be available in the app
    envPrefix: ['VITE_', 'REACT_APP_'],
  };
});