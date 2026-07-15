---
# isaac-xapx
title: bb spec/features run native in babashka, not shell out to clojure
status: draft
type: feature
priority: normal
created_at: 2026-07-15T17:38:39Z
updated_at: 2026-07-15T17:38:39Z
---

## Goal (Micah, 2026-07-15)

`bb spec` / `bb features` / `bb ci` in isaac-agent must run speclj/gherclj **natively in babashka**, NOT shell out to `clojure -M:spec`. Native bb is far faster (no JVM cold-start) and streams output; shelling clojure throws away bb's whole speed advantage. "bb should not shell out to clojure."

## Evidence

isaac-agent `bb.edn` `spec` task: `(apply sh/sh "clojure" "-M:spec" *command-line-args*)` — a thin wrapper around JVM clojure. `features` and `ci` likewise shell `clojure -M:features` / `-M:spec`. So `bb spec` == `clojure -M:spec` + bb boot + OUTPUT BUFFERING (the task buffers out/err and prints at the end, so workers watching get no live feedback — a worker on isaac-n9ez visibly abandoned `bb spec` for `clojure -M:` for exactly this reason).

## The pattern to copy — isaac-foundation already does this right

foundation `bb.edn` uses a `bb.test-tasks` namespace:
- `spec`     -> `(tests/run-spec! …)`      — NATIVE bb speclj
- `ci`       -> `(tests/run-ci!)`          — native
- `jvm-spec` -> `(tests/run-jvm-spec! …)`  — explicit JVM FALLBACK (kept for specs that genuinely need the JVM)
- `jvm-features` -> `(tests/run-jvm-features! …)`

Copy this shape into isaac-agent: native `spec`/`features`/`ci`, plus `jvm-spec`/`jvm-features` fallbacks. Ensure bb.edn :deps carry speclj + what native running needs.

## Acceptance (parity is the gate — do not blind-flip)

- [ ] `bb spec` and `bb features` run natively in bb (no `clojure -M` shell-out) and STREAM output.
- [ ] PARITY: the FULL spec + non-wip feature suite passes natively with the SAME results as the JVM run. Any spec/feature that genuinely requires the JVM is moved to (or documented against) `jvm-spec`/`jvm-features`, not silently dropped.
- [ ] Record the before/after wall-clock for `bb ci` in the bean notes (the speed win is the point).
- [ ] `bb ci` (the verification gate) uses the native path.

## Scope

isaac-agent first (the one that bit us). Then AUDIT the other module repos (isaac-hail, isaac-acp, isaac-cli-server, isaac-discord, …) — any whose bb.edn shells `clojure` in spec/features/ci gets the same fix. List findings in the bean; split per-repo if large.

## Risk

Some specs may use JVM-only interop and fail natively — that is EXPECTED and is what `jvm-spec` is for. The parity check surfaces them; route those to the JVM task rather than forcing them native.
