---
# isaac-ciiz
title: 'isaac hail send --root <dir> fails: ''hail queue requires :root'' (root not on CLI command path)'
status: completed
type: bug
priority: normal
tags: []
created_at: 2026-06-20T21:22:55Z
updated_at: 2026-06-20T21:30:27Z
---

`isaac hail send` cannot resolve `--root` outside a running server, so the CLI
entrypoint is unusable standalone.

## Repro
    isaac --root /Users/zane/.isaac hail send --crew main --prompt "ping" --edn
    => ExceptionInfo: hail queue requires :root
       isaac.hail.queue/runtime-root  (or (loader/root) (throw ...))
(Packaged 0.1.5 on zanebot; hail module 4d06719 / v0.1.2.)

## Root cause
Two root accessors disagree on the CLI command path:
- `isaac.hail.queue/runtime-root` calls `isaac.config.loader/root`
  (loader.clj:959) = `(or (nexus/get :root) (:root (snapshot ...)))`.
- `isaac.main/run` CLI dispatch (main.clj:120) binds `isaac.config.root/*root*`
  to the resolved root, but inits the nexus with `{:fs fs*}` only — no `:root` —
  and a bare command like `hail send` never loads a full config snapshot.
- So `nexus/:root` is absent AND snapshot `:root` is absent => `loader/root`
  returns nil => throw.

`modules list` works because it reads root from the opts map / `*root*`, not the
ambient `loader/root`. The hail module assumes the server's ambient root, which
only exists in a server context.

## Fix options
- (A, recommended — foundation) In `main/run` CLI dispatch install the resolved
  root into the nexus: `(nexus/init! {:fs fs* :root resolved-root})` so any
  command relying on ambient `loader/root` works from the CLI. One-line, fixes
  the whole class.
- (B — hail) Resolve root from the passed opts (`:root`/`:display-root`) or
  `isaac.config.root/current-root` instead of ambient `loader/root`.

Prefer A unless there's a reason CLI commands must NOT see a nexus :root slot
(loader/root's docstring says production relies on the config snapshot, not the
nexus slot — A only adds the slot for the CLI path, leaving server behavior
unchanged).

## Acceptance
- `isaac --root <dir> hail send --crew <c> --prompt <p>` persists a record to
  <dir>/hail/pending and prints the id (exit 0), with no running server.
- Existing server hail path (HTTP /hail/send, delivery worker) unchanged.


## Handoff (work-3)

- **Fix:** `isaac-foundation` `b923282` — CLI dispatch passes `:root resolved-root` to `nexus/init!`.
- **Test:** extended `main_spec` "installs the active fs and resolved root into runtime init".
- **Verified:** `bb ci` in isaac-foundation; `bb features features/send.feature` in isaac-hail (local foundation).

## Verification Notes

- Verification passed on fetched GitHub `isaac-foundation` `b923282` and the current hail release head `4d06719`, not the stale local mirrors.
- `bb ci` in `isaac-foundation` passed: `760 examples, 0 failures, 1328 assertions`.
- The direct standalone CLI repro now works without a running server: from the hail release worktree, `clojure -Sdeps '{:deps {io.github.slagyr/isaac-foundation {:local/root "/private/tmp/isaac-ciiz-foundation"}}}' -M -m isaac.main --root /private/tmp/isaac-ciiz-proof-root hail send --band bean-pickup --prompt ping --edn` exited 0 and printed a hail record with `:id "hail-1"`.
- The fix is the one-line CLI runtime install in [isaac-foundation/src/isaac/main.clj](/Users/micahmartin/agents/verify/isaac-foundation/src/isaac/main.clj:120), and the accompanying regression coverage is in [spec/isaac/main_spec.clj](/Users/micahmartin/agents/verify/isaac-foundation/spec/isaac/main_spec.clj:239).
- This closes the specific bug surface from the bean: `hail.queue/runtime-root` in the hail release still reads ambient `loader/root`, and that ambient root is now present on the standalone CLI path.
