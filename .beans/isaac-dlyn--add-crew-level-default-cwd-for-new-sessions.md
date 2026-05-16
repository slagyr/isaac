---
# isaac-dlyn
title: Add crew-level default cwd for new sessions
status: todo
type: feature
priority: high
tags:
    - deferred
created_at: 2026-05-03T17:19:52Z
updated_at: 2026-05-16T18:56:48Z
---

## Problem

Crew config has `:model`, `:soul`, `:tools`, `:context-mode` — but no `:cwd`. Each entrypoint sets new-session cwd ad hoc:

- Discord uses state-dir
- ACP falls back to startup `user.dir`
- Hooks pass `:cwd quarters` explicitly
- CLI prompt uses process cwd

So a crew's soul and its filesystem workspace are unrelated, and the "where does this crew live?" answer depends on which channel woke it up.

## Design

Add `:cwd` to the crew schema. When a new session is created, the cascade resolves to one of:

> **explicit session override > crew :cwd > channel default**

`:cwd` is **state-defining** (per the pattern in isaac-q90z): resolved **once at session creation** and locked onto the session sidecar. Not re-resolved per turn. Mid-session edits to crew config do not retroactively move existing sessions.

Model and provider tiers don't apply — they have no workdir concept. The cascade for `:cwd` is just the three layers above.

### Schema

```clojure
:cwd {:type     :string
      :validate (fn [v] (or (nil? v) (and (string? v) (absolute? v))))
      :message  "must be an absolute path"
      :description "Default workdir for new sessions on this crew"}
```

Absolute paths only. `~` expansion is YAGNI for v1.

### Resolution timing

Per isaac-q90z's framework:
- **Behavioral parameters** (`:effort`, `:model`, `:context-mode`) — resolve fresh each turn
- **State-defining settings** (`:history-retention`, **`:cwd`**) — resolve at session create, lock to sidecar

The implementer should reuse q90z's cascade helper (or the `resolve-effort` shape it references at `src/isaac/effort.clj:13`) so `:cwd` and `:history-retention` share one resolution path.

## Feature spec

`features/session/crew_cwd.feature` (committed with `@wip`):

| # | Scenario | Line |
|---|---|---|
| 1 | Crew :cwd seeds the new session's cwd | features/session/crew_cwd.feature:11 |
| 2 | Explicit session :cwd overrides crew :cwd | features/session/crew_cwd.feature:30 |
| 3 | Crew :cwd must be an absolute path | features/session/crew_cwd.feature:51 |

Run: `bb features features/session/crew_cwd.feature`

Zero new step definitions needed — all assertions reuse existing steps.

## Acceptance

- [ ] `:cwd` field added to crew schema with absolute-path validation
- [ ] Session creation resolves `:cwd` via the cascade: explicit override > crew > channel default
- [ ] Resolved value is written to the session sidecar at create-time; subsequent turns read from sidecar
- [ ] Crew config changes do not retroactively rewrite existing sessions' `:cwd`
- [ ] `@wip` removed from `features/session/crew_cwd.feature`; all three scenarios pass
- [ ] Unit specs cover the cascade order (all three precedence steps tested independently)

## Out of scope

- `~` expansion or other path conveniences
- Per-channel default cleanup (Discord state-dir, ACP user.dir, hooks quarters, CLI process cwd) — those stay as the channel-default tier
- Refactoring isaac-q90z's resolution helper into a shared utility — that lift-out can happen as part of either bean; not a blocker

## Related

- **isaac-q90z** (history-retention) — establishes the state-defining pattern and the cascade shape; `:cwd` follows the same model. Either bean can land first; whichever lands second should reuse the resolution helper.
- **isaac-c7e7** (default exec to session cwd) — already done; this bean makes the session's `:cwd` more meaningful by giving crews a way to set it without ad-hoc per-channel logic.
