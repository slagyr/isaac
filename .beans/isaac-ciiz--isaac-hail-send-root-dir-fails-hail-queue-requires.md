---
# isaac-ciiz
title: 'isaac hail send --root <dir> fails: ''hail queue requires :root'' (root not on CLI command path)'
status: in-progress
type: bug
priority: normal
created_at: 2026-06-20T21:22:55Z
updated_at: 2026-06-20T21:24:12Z
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
