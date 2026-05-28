# Avatar Maker

A ClojureScript single-page app for creating, customizing, and sharing
SVG avatars. Avatar configurations are persisted to `localStorage` so
work survives reloads, and the app is installable as a PWA.

## Stack

- **ClojureScript** compiled with [shadow-cljs](https://github.com/thheller/shadow-cljs)
- **Reagent** (React 18) for the UI
- **Tachyons** for utility CSS (loaded from a CDN in `public/index.html`)
- **GitHub Pages** for hosting, with versioned build history assembled
  by `scripts/assemble-versioned-site.mjs`

## Requirements

- Node.js 20+
- A JDK (21 recommended — matches CI) for shadow-cljs

## Setup

```sh
npm ci
```

## Common tasks

| Command              | What it does                                                  |
| -------------------- | ------------------------------------------------------------- |
| `npm run dev`        | shadow-cljs watch + dev server on http://localhost:8080       |
| `npm run build`      | Release build into `public/js/`                               |
| `npm run preview`    | Serve the release build from `target/shadow/app` on port 8080 |
| `npm test`           | Compile and run the node-test build (`target/test.cjs`)       |
| `npm run test:watch` | Recompile tests on change                                     |
| `npm run check`      | `npm test && npm run build`                                   |

`npm run dev` starts shadow-cljs in watch mode and serves `public/` at
http://localhost:8080 (configured in `shadow-cljs.edn` under
`:dev-http`). Hot reload is wired through `avatar.core/rerender`.

## Project layout

```
public/                static assets (index.html, global.css, manifest, service worker)
src/avatar/
  core.cljs            entry point + React root + hot reload hook
  ui.cljs              top-level UI (main panel, layout, controls)
  ui/components.cljs   reusable Reagent components
  render.cljs          SVG rendering of the avatar
  icons.cljs           SVG icon parts
  config.cljs          static configuration (parts, palettes, etc.)
  state.cljs           app-state ratom
  db.cljs              shape of the persisted avatar
  storage.cljs         localStorage load/save + watchers
  version.cljs         build version stamp
test/avatar/           node-test specs (render, ui)
scripts/               CI helpers (versioned-site assembler)
.github/workflows/     GitHub Pages deploy
```

## Deployment

Pushes to `main` trigger `.github/workflows/pages.yml`, which:

1. Builds the release with `npm run build`.
2. Runs `scripts/assemble-versioned-site.mjs` to combine the current
   build with prior successful runs, exposing each as
   `/<run-number>/` and `/<sha7>/` aliases alongside the root.
3. Publishes the result as a GitHub Pages artifact.

The script also writes `versions.json`, `version-index.txt`, and
`version-diagnostics.txt` to the deployed site so older builds remain
browsable.
