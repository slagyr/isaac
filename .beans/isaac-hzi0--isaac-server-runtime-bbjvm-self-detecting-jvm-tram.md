---
# isaac-hzi0
title: isaac server --runtime bb|jvm — self-detecting JVM trampoline
status: in-progress
type: feature
priority: normal
created_at: 2026-06-21T01:09:00Z
updated_at: 2026-06-21T02:22:16Z
parent: isaac-5zfv
---

Add `--runtime bb|jvm` (default `bb`) to `isaac server` (`isaac.server.cli`). When
jvm is requested from a bb process, re-exec into the JVM; otherwise run in-process
as today. Part of epic isaac-5zfv. Blocked by isaac-smmm (uses its classpath fn).

## Behavior
- Detect current runtime via `babashka.version` (set under bb, absent on JVM).
- `--runtime jvm` AND currently bb  -> compute the classpath (smmm's function,
  in-process), then `exec clojure -Scp "<cp>" -M -m isaac.main --root <root>
  server` (exec-replace = one process; pass through original args/root).
- `--runtime jvm` AND already JVM (or `--runtime bb`) -> start the server
  in-process. (This guards against an infinite trampoline.)
- `--runtime jvm` with clojure absent -> clear error to stderr:
  "--runtime jvm requires the clojure CLI (brew install clojure)"; non-zero exit.

## Scenarios (DRAFT — pending Micah review; do not generate feature file yet)
```gherkin
Scenario: default runtime starts the server in-process (bb)
  When isaac is run with "server" with no --runtime
  Then the server starts in the current process (no re-exec)

Scenario: --runtime jvm from a bb process re-execs into clojure
  Given the process is running under Babashka
  When isaac is run with "server --runtime jvm"
  Then it execs a clojure command of form
       "clojure -Sdeps <deps-map> -M -m isaac.main ... server"

Scenario: --runtime jvm while already on the JVM runs in-process
  Given the process is running on the JVM (no babashka.version)
  When server is invoked with "--runtime jvm"
  Then the server starts in-process and does NOT re-exec

Scenario: --runtime jvm errors clearly when clojure is unavailable
  Given the clojure CLI is not on PATH
  When isaac is run with "server --runtime jvm"
  Then stderr contains "requires the clojure CLI"
  And  the exit code is non-zero
```
(Note: the re-exec scenarios likely land as unit specs on the runtime-dispatch
fn + one @slow feature that genuinely trampolines; split during speccing.)

## Acceptance
- `server` default behavior unchanged (bb, in-process).
- `server --runtime jvm` boots the JVM server (proven in spike) via smmm's cp.
- No infinite trampoline; missing-clojure error is actionable.

## Decision (2026-06-20): trampoline uses -Sdeps, not -Scp

The `--runtime jvm` trampoline computes the `--edn` deps map in-process (smmm's
`compose-module-deps-map`) and `exec`s
`clojure -Sdeps '<map>' -M -m isaac.main --root <root> server`.
One JVM, no shell-out, clojure supplies itself. Supersedes the earlier `-Scp`
framing in the epic/bean.



## Verification failed

HEAD: 9c27d36747e704532f5d225c3aa89dca8dedbd57
Working tree: clean

Current isaac-server main does not compile on the targeted proof. Running `bb spec spec/isaac/server/runtime_spec.clj spec/isaac/server/cli_spec.clj` fails while loading `src/isaac/server/runtime.clj` with `No such var: module-loader/config->launch-deps`. `isaac-server` still pins `io.github.slagyr/isaac-foundation` to `455e0db8cb48de547b06f0e150079f5b566979e3` in `deps.edn`, which predates smmm's `config->launch-deps` addition in foundation `06a271e4c18ffc92421e84f752b10a89db3c4e35`. The trampoline implementation is present, but the repo head is not consumable, so acceptance is not met.
