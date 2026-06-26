---
# isaac-4onv
title: Remove dynamic fs/*fs*; specs install :fs via nexus
status: todo
type: task
priority: high
created_at: 2026-06-26T21:18:52Z
updated_at: 2026-06-26T21:18:52Z
parent: isaac-jw6d
---

Problem

`fs/instance` still falls back to thread-local `*fs*`. Production `src/` no longer
binds it, but ~18 spec files still do — so the jw6d invariant is not enforced and
tests can pass while hiding ambient-fs regressions.

Scope

- Delete `def ^:dynamic *fs*` from `isaac.fs`
- `fs/instance` reads only `(:fs source)` or `(nexus/get :fs)`; throw if neither is set
- Migrate remaining `binding [fs/*fs* ...]` specs to `nexus/-with-nexus {:fs ...}`,
  `nexus/install!`, or `nexus/init!`
- Remove spec-local `(def ^:dynamic *fs* ...)` shims that exist only for the old pattern

Out of scope

- Threading `:fs` through `config.loader` (next jw6d child)
- Removing `(fs/instance)` call sites in production `src/`

Surface

- `isaac-foundation/src/isaac/fs.clj` — remove dynamic var and fallback branch
- Spec files in foundation, agent, server, discord, imessage that still bind `*fs*`

Acceptance

- `rg '\*fs\*'` over the ecosystem returns no matches
- `isaac-foundation` `bb spec` green
- `isaac-agent` and `isaac-server` `bb spec` green
- Existing specs titled "without binding fs/*fs*" still pass

Notes

Child of isaac-jw6d. Completes the `*fs*` half of the epic's thread-local removal;
`config.loader` explicit `:fs` is the recommended follow-on bean.
