import { defineConfig } from "vite";
import path from "path";

export default defineConfig({
  server: {
    middlewareMode: false,
    hmr: true,
  },
  build: {
    outDir: "dist",
    emptyOutDir: true,
  },
});