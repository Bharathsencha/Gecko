import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'

// Built into ../docs, which GitHub Pages serves. Relative base so it works under /Gecko/.
export default defineConfig({
  base: './',
  plugins: [svelte()],
  build: { outDir: '../docs', emptyOutDir: true }
})
