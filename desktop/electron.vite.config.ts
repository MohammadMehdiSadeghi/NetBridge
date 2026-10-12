import { defineConfig } from 'electron-vite'
import react from '@vitejs/plugin-react'
import type { Plugin } from 'vite'
import { resolve } from 'path'

/**
 * The Content-Security-Policy lives in the HTML so it also applies to the packaged
 * `file://` build. Vite's React Fast Refresh preamble is injected as an inline
 * `<script type="module">`, which `script-src 'self'` rejects — so during `dev` we
 * swap the policy for a relaxed one and leave the shipped policy untouched.
 */
const PROD_CSP =
  "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:"

const DEV_CSP =
  "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; " +
  "img-src 'self' data:; connect-src 'self' ws: http://localhost:* http://127.0.0.1:*"

function devCsp(): Plugin {
  return {
    name: 'netbridge-dev-csp',
    apply: 'serve',
    transformIndexHtml(html) {
      return html.replace(PROD_CSP, DEV_CSP)
    }
  }
}

export default defineConfig({
  main: {
    resolve: {
      alias: {
        '@main': resolve(__dirname, 'src/main')
      }
    },
    build: {
      rollupOptions: {
        input: resolve(__dirname, 'src/main/index.ts')
      }
    }
  },
  preload: {
    resolve: {
      alias: {
        '@preload': resolve(__dirname, 'src/preload')
      }
    },
    build: {
      rollupOptions: {
        input: resolve(__dirname, 'src/preload/index.ts')
      }
    }
  },
  renderer: {
    resolve: {
      alias: {
        '@renderer': resolve(__dirname, 'src/renderer/src')
      }
    },
    plugins: [react(), devCsp()]
  }
})
