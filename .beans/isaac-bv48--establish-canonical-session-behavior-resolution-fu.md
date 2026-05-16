---
# isaac-bv48
title: Establish canonical session-behavior resolution funnel
status: draft
type: feature
priority: high
created_at: 2026-05-16T19:24:31Z
updated_at: 2026-05-16T19:24:31Z
---

## Problem

Today every site that needs session-effective behavior pulls the session and reads fields ad-hoc:

- `src/isaac/drive/turn.clj:200, 380, 622` — multiple sites in build-turn-ctx and friends
- `src/isaac/session/compaction.clj:278`
- `src/isaac/hooks.clj:186`
- `src/isaac/comm/acp/server.clj:47, 144, 206`
- `src/isaac/comm/acp/websocket.clj:67`
- `src/isaac/comm/acp/cli.clj:436`

Each site reads different fields, resolves different cascades inline, and uses different fallback logic. There's no canonical answer to "what is this session's effective behavior right now?"

This drifts. A new field requires updating every call site. The cascade rules live in N places. Worst case, different sites disagree on the effective value for the same session.

## Approach

A single namespace and single function:

```clojure
;; src/isaac/session/context.clj  (new)
(defn resolve-behavior
  "Returns the fully-resolved behavior map for `session-key`. Combines
   sidecar-locked fields (state-defining, e.g. :history-retention) with
   cascade-resolved fields (behavioral, e.g. :effort, :context-mode,
   :model). One call site, one source of truth for what a session 'is'
   at any given moment."
  [session-key]
  ...)
```

The funnel internally knows:
- Which fields are sidecar-locked (read straight from sidecar; no cascade)
- Which fields cascade (run `session > crew > model > provider > defaults > hardcoded` chain)
- The defaults for each

All existing readers migrate to call `resolve-behavior` instead of `store/get-session` + inline field lookup.

## Mirror funnel at session creation

The companion to runtime resolution is creation. A single creation funnel:

```clojure
;; same ns, perhaps
(defn create-with-resolved-behavior!
  "Resolves the cascade for sidecar-locked fields and writes them onto
   the new session sidecar atomically."
  [session-key opts] ...)
```

Every session-creator (CLI, hooks, ACP, cron, slash, programmatic) calls this. The funnel handles cascade resolution and sidecar persistence; creators just pass any explicit overrides they have.

## Scope

- New `isaac.session.context` namespace with `resolve-behavior` and `create-with-resolved-behavior!`
- Migrate the listed call sites to use the funnel
- Document the locked-vs-cascade distinction in the funnel implementation
- Add a `:session/behavior-resolved` debug log on each call for observability

## Out of scope

- New session fields (those come from `isaac-q90z` and similar)
- Slash command for session inspection (separate concern)
- Performance optimization / caching (resolution is cheap; revisit if profiling says otherwise)

## Relationship to other beans

- **`isaac-q90z`** (history retention) — blocked-by this. `:history-retention` is the first sidecar-locked field; the funnel is where it's added. q90z's scope becomes "implement `:history-retention` semantics on top of the funnel."
- **`isaac-cdqk`** (context-mode) — already shipped. Can be migrated to use the funnel as a follow-up to simplify `build-turn-ctx:653`.
- **`isaac-xwwb`** (transcript rotation) — transitively depends; rotation needs the funnel to know the session's retention policy.

## Acceptance

The funnel exists, all listed call sites route through it, and existing tests pass unchanged. The locked-vs-cascade distinction is encoded in one place. Adding `:history-retention` becomes a one-line addition in the funnel.

## Feature file

This is a refactor — observable behavior doesn't change. Coverage is via existing specs that already exercise these call sites; no new `.feature` is needed. If a regression seam is wanted later, a single scenario asserting `resolve-behavior` returns the right map for a configured session would suffice.
