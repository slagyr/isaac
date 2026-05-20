---
# isaac-ige7
title: Config reloader fires on non-config files (.bak, .DS_Store, etc.)
status: todo
type: bug
priority: normal
created_at: 2026-05-20T18:23:08Z
updated_at: 2026-05-20T18:23:08Z
---

## Gap

`isaac.config.change-source-bb` watches `~/.isaac/config/` recursively and
queues a reload for every file change. The only filter is
`editor-artifact?` in `isaac.config.change-source-protocol`, which catches
vim/emacs swap and lockfile patterns (`~`, `.swp`, `.swo`, `.swx`, `.#foo`,
`#foo#`, numeric-only filenames) but lets through:

- `.bak`, `.backup`, `.orig`, `.tmp`
- `.DS_Store` (Finder writes these constantly on macOS)
- Any arbitrary suffix (e.g. `isaac.edn.bak.before-discord-namespace`)

When such a file changes, `reload-config!` (`isaac.server.app/reload-config!`)
runs a full reload + reconcile. The reload itself reads the *correct*
config files via `config/load-config-result` — the spurious file is never
parsed. But the log line still emits `:config/reloaded :path <triggering-path>`,
which reads as if the triggering file became active config. It did not.

## Observed example

```
11:15:15.692  INFO   :config/reloaded  {:path "isaac.edn.bak.before-discord-namespace"}
```

A change to `isaac.edn.bak.before-discord-namespace` triggered a full
reload + reconcile. The `.bak` was never loaded; the log is misleading.

## Impact

- **Functional:** low — the loaded config is correct.
- **Performance:** moderate — full reconcile on every spurious event;
  on macOS, `.DS_Store` writes can fire frequently.
- **Debugging:** high — the log message looks like a backup file became
  active config.

## Proposed change

**Allowlist by known config shape.** The config layout is centralized in
`isaac.config.paths`:

- `isaac.edn` (root)
- `crew/<id>.md`
- `cron/<id>.md`
- `hooks/<id>.md`
- `<kind>/<id>.edn` (entity files)

Introduce a predicate, e.g. `(paths/config-file? relative-path)`, that
returns true iff a relative path matches one of those shapes. Wire it
into `enqueue-change!` in `change_source_bb` so paths failing the
allowlist are silently dropped (or, see below, logged at debug).

Allowlisting beats extending `editor-artifact?` because the set of "files
that look like config" is finite and known, while the set of "files some
tool decided to drop next to config" is unbounded.

## Surface

- `src/isaac/config/paths.clj` — add `config-file?` predicate (or
  `loadable-path?` / similar — naming TBD by implementer).
- `src/isaac/config/change_source_bb.clj` — `enqueue-change!` consults
  the predicate before queueing.
- Tests:
  - Existing change-source specs gain cases for `.bak`, `.DS_Store`,
    dotfiles, and unknown-extension files (all should NOT enqueue).
  - Positive cases preserved: `isaac.edn`, `crew/foo.md`, `cron/bar.md`,
    `hooks/baz.md`, `<kind>/<id>.edn`.

## Open questions

1. **Log the rejected events at debug, or stay silent?** Useful for
   diagnosing "why didn't my config change apply?" but adds noise.
   I'd lean `log/debug :config/ignored :path <p>` so it's visible
   when chasing a problem but invisible otherwise.
2. **Should the allowlist live in `paths`, or in a new
   `isaac.config.layout` namespace?** `paths` is currently pure path
   construction; adding a predicate is a small scope creep but the
   knowledge belongs together.
3. **Anything outside `<config>/` ever trigger a reload?** Today no, but
   if hooks/scripts referenced from the config (e.g., cron `.md` files
   that exec scripts elsewhere) ever needed watching, this would need
   to extend. Out of scope for this bean.

## Origin

Observed in zanebot's log:
`11:15:15.692 INFO :config/reloaded {:path "isaac.edn.bak.before-discord-namespace"}`
Surfaced while reviewing config-reload behavior with Micah.
