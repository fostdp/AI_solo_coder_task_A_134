import { defineConfig } from 'vite';
import { resolve } from 'path';
import { createGzip } from 'zlib';
import { createWriteStream, mkdirSync, existsSync, readFileSync, writeFileSync, unlinkSync } from 'fs';
import { globSync } from 'glob';

function viteGzipPlugin(options = {}) {
  const {
    threshold = 1024,
    minRatio = 0.85,
    gzipOptions = { level: 9 }
  } = options;
  return {
    name: 'vite-plugin-gzip',
    apply: 'build',
    closeBundle() {
      const distDir = resolve('dist');
      const files = globSync(`${distDir}/**/*.{js,css,html,json,svg,xml}`);
      let totalSaved = 0;
      files.forEach(file => {
        const stat = readFileSync(file);
        if (stat.length < threshold) return;
        const gzFile = file + '.gz';
        const out = createWriteStream(gzFile);
        const gz = createGzip(gzipOptions);
        out.on('finish', () => {
          const orig = stat.length;
          const comp = readFileSync(gzFile).length;
          const ratio = comp / orig;
          if (ratio >= minRatio) {
            try { unlinkSync(gzFile); } catch (e) {}
          } else {
            totalSaved += (orig - comp);
          }
        });
        out.on('error', () => {});
        gz.pipe(out);
        gz.write(stat);
        gz.end();
      });
    }
  };
}

export default defineConfig({
  server: {
    port: 3000,
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws': {
        target: 'http://localhost:8080',
        ws: true,
        changeOrigin: true
      },
      '/actuator': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    minify: 'terser',
    target: 'es2019',
    cssMinify: true,
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-three': ['three'],
          'vendor-chart': ['chart.js'],
          'vendor-ws': ['sockjs-client', 'stompjs'],
          'vendor-core': ['vue']
        },
        chunkFileNames: 'assets/js/[name]-[hash].js',
        entryFileNames: 'assets/js/entry-[hash].js',
        assetFileNames: (info) => {
          if (info.name && info.name.endsWith('.css')) {
            return 'assets/css/[name]-[hash][extname]';
          }
          return 'assets/[name]-[hash][extname]';
        }
      },
      treeshake: true
    },
    reportCompressedSize: true,
    chunkSizeWarningLimit: 1500
  },
  plugins: [
    viteGzipPlugin({ threshold: 1024 })
  ],
  resolve: {
    alias: {
      '@': resolve('src')
    }
  },
  optimizeDeps: {
    include: ['three', 'chart.js', 'sockjs-client', 'stompjs'],
    exclude: []
  }
});
