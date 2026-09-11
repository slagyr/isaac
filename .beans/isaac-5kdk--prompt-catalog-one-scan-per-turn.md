---
# isaac-5kdk
title: 'Prompt catalog: one scan per turn'
status: completed
type: task
priority: normal
created_at: 2026-08-27T20:50:44Z
updated_at: 2026-08-27T21:00:20Z
parent: isaac-nwj3
---

Likely repo: `isaac-agent`. Parent: isaac-nwj3.

## Problem

`resolve-catalog` fully rescans prompt roots (list `.md`, slurp, parse YAML frontmatter) on every call. A turn hits it at least twice in `build-turn` (`read-skill-disclosure` + `read-rules-text`) and again on every `skill__list` / `skill__load`. Debug `:prompt/catalog-resolved` is the instrumentation from isaac-nwj3; zanebot shows ~7–20ms × N per turn.

## Vocabulary (do not invent new nouns)

Isaac already has the unit of work:

| Term     | Meaning |
|----------|---------|
| `charge` | Sealed bundle that *drives* a turn (the cartridge). Comms build it; drive consumes it. |
| `turn`   | Drive execution of one charge (the shot). |

The prompt **catalog** is not that. It is a derived frontmatter index of commands/skills/rules. Cache it for the **turn**. Do not hang it on the charge (charge is comm-supplied and sealed). Do not name this slug/round/shot.

## Behavior

- Bind a turn-scoped catalog cache around `run-turn!` so `build-turn` and the tool loop share one scan.
- `resolve-catalog` reuses the bound catalog when `root` / `cwd` / config prompt paths match; otherwise scans (cwd change mid-turn still works).
- No bind (specs, CLI one-shots, slash before drive) → today's scan-every-call.
- Bodies stay lazy (`body-loader`). Index is what we cache.
- No process-lifetime cache, no fs watcher, no walk-from-cwd invalidation. Next turn scans again, so skill edits show up.
- `:prompt/catalog-resolved` stays debug and fires **once per actual scan**, not per lookup.

## Specs

`spec/isaac/prompt/catalog_spec.clj`

- Two `resolve-catalog` calls **without** a turn bind → two `:prompt/catalog-resolved` logs.
- Two `resolve-catalog` calls **under** the turn bind → one log, same catalog contents.
- Bind + different cwd/root that discovers another project → second scan.

If `run-turn!` is the bind site, one drive spec that `build-turn`'s dual reads plus a `skill__load` in the same turn produce a single scan.

## Acceptance

```
bb spec spec/isaac/prompt/catalog_spec.clj
```

Existing `features/prompts/catalog.feature` stays green (no `@wip` required unless a scenario's expected log count changes). Update that file's header comment: per-turn cache is the decision; debug log remains once-per-scan.

## Out of scope

- Process-level / mtime / watch cache.
- Putting catalog on the charge schema.
- Slash-command catalog resolve before `run-turn!` (one extra scan is fine).
