---
# isaac-5zfv
title: 'Selectable server runtime: run isaac as bb or JVM'
status: draft
type: epic
created_at: 2026-06-21T01:08:19Z
updated_at: 2026-06-21T01:08:19Z
---

Let the server run on either Babashka (fast CLI, today's default) or the JVM
(JIT-compiled, real threads, long-running optimizations), selectable per the
`server` and `service` commands. Grounded by a spike: isaac boots on the JVM
unchanged (core + modules + server bind), and `clojure -Scp "<generated>" -M -m
isaac.main server` launches with NO deps.edn maintained.

## Spike findings (zanebot, isaac 0.1.6)
- Portability: runs as-is, zero code changes (only a benign `reset!` shadow warn).
- Speedup: ~1.3x I/O-bound (config), ~2x compute-bound (request building); JSON
  encode (cheshire) ~1x. So ~2x on per-turn prep — real, not transformative.
  (Algorithmic fixes hgf6/m14k matter more for per-turn cost; JVM compounds.)
- Launch proven: `clojure -Scp "$(modules deps --classpath)" -M -m isaac.main
  server` works from a dir with no deps.edn; generated cp includes the clojure jar.

## Shape (3 children)
1. `isaac modules deps [--classpath|--edn]` (foundation) — generate the JVM
   launch classpath/deps from foundation seed + config :modules + 92p3 exclusions
   + injected org.clojure/clojure. Reuses the loader's existing coord resolution
   (factor out of compose-config-modules!). **Blocks the other two.**
2. `isaac server --runtime bb|jvm` (isaac-server) — self-detecting trampoline:
   on bb + jvm requested -> exec `clojure -Scp "<deps>" -M -m isaac.main server`;
   already on JVM or bb -> in-process (no re-exec). Uses #1 in-process.
3. `isaac service install --runtime bb|jvm` (isaac-server) — bake `server
   --runtime <X>` into the launchd plist; show runtime in `service status`.

## Notes
- No materialized deps.edn — classpath derived fresh each launch from config, so
  `modules install/upgrade` is auto-reflected on restart. Config stays SoT.
- jvm needs clojure + JDK on host; formula declares them / clear error otherwise.
- Independently shippable from dps/jw6d (those harden the JVM server under real
  concurrency; this just exposes the option).
