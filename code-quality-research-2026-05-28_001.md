# Code Quality Research — avatar-maker — 2026-05-28 (001)

Takeaways from a 2nd code-quality-analysis run against this repo.

- **Tool:** `code-quality-analysis` (`~/Documents/Study/AI/avi_drucker/code-quality-analysis`)
- **Config:** `examples/avatar-maker.edn`
- **Command:** `./assess.bb examples/avatar-maker.edn`
- **Result:** exit 0, 15 deterministic checks, ~9.9s. Byte-identical to the 1st run (only the report timestamp changed) — correct for an all-deterministic config, and a sign the run is reproducible.

## Scorecard

| Concern | Verdict | Notes |
|---|---|---|
| correctness | PASS | tests pass, no FIXME/TODO |
| delivery-safety | PASS | lockfile, shadow config, CI, node pinned, clean tree, README |
| testability | PASS | test files exist, none commented out |
| readability | **WARN** | `no-deep-nesting-in-src` FAIL |
| maintainability | **WARN** | `max-src-file-loc-bound` FAIL |

Both failures are advisory — nothing blocks.

## What to improve

### 1. Two oversized files dominate the codebase
`max-src-file-loc-bound` (800-line cap) FAIL is real:

- `src/avatar/ui.cljs` — **1699 lines**
- `src/avatar/render.cljs` — **1496 lines**

These two are ~76% of the 4185-line `src/`; everything else is ≤248 lines. Natural splits:
- `render.cljs` → by body part / SVG component group.
- `ui.cljs` → move panel components, event handlers, and layout into `src/avatar/ui/` (already exists alongside `components.cljs`).

### 2. Test depth is thin
91 lines of test vs 4185 of source (~2%). `tests-not-empty` passed because it only checks that *a* test file exists, not coverage. The two largest, most logic-dense files (`render`, `config`) are where regressions will hide. Add tests around `clamp-cfg`, gradient/color resolution, and config defaults.

### 3. The deep-nesting FAIL is mostly a false alarm — but points at the real issue
The flagged 16-space-deep lines are overwhelmingly **Hiccup/SVG data literals** (`:fill`, `:stop-color`, `:radialGradient` maps), not control-flow nesting. Not a classic readability bug. The genuine cost is upstream: render functions large enough that their markup drifts 8+ levels deep. Splitting per #1 dissolves this naturally.

## Highest-leverage move
Split `ui.cljs` and `render.cljs`. It fixes the maintainability WARN and dissolves the readability WARN at the same time.
