---
# isaac-o3da
title: "SessionStore protocol: unify backends, drop legacy keys, schema metadata"
status: completed
type: feature
priority: normal
created_at: 2026-05-07T23:05:54Z
updated_at: 2026-05-08T15:13:43Z
---

## Description

Today the "session" abstraction is split across two implementations with overlapping names and no protocol uniting them:

- isaac.session.storage (~17 public fns, file-backed, every call takes state-dir + key)
- isaac.session.store    (5 fns, in-memory, alternative shape)

Plus the legacy key format `agent:channel:chatType:conversation` that's no longer how session names work — sessions are named via :sequential or :adjective-noun strategies. parse-key/build-key exist to satisfy this dead format and bleed into storage.clj internals (legacy-key?, session-id, slugify, entry-defaults).

This bead introduces a SessionStore protocol that unifies both backends, removes the legacy key format, formalizes session metadata via schema, and lifts naming strategy into a reusable abstraction. It is the prerequisite for the deferred sidecar refactor (isaac-kf7q).

## Target protocol

```clojure
(defprotocol SessionStore
  (open-session!  [this name opts])
  (delete-session! [this name])
  (list-sessions  [this])
  (list-sessions-by-agent [this agent])
  (most-recent-session [this])
  (get-session    [this name])
  (get-transcript [this name])
  (update-session! [this name updates])
  (append-message!    [this name message])
  (append-error!      [this name error])
  (append-compaction! [this name compaction])
  (splice-compaction! [this name compaction])
  (truncate-after-compaction! [this name]))
```

## Session metadata schema (verified against actual writes)

22 fields observed across create-session!, update-session!, and update-tokens! callers. Define under isaac.session.schema using c3kit.apron.schema.

Identity:
- :id (string, required) — derived from name
- :key (string) — same as id; legacy form on legacy-imported sessions (deprecate)
- :name (string, required)
- :sessionId (string) — id of the JSONL transcript header entry
- :session-file (string) — `<id>.jsonl`

Provenance / context:
- :origin (map) — {:kind :cli|:acp|:webhook|:cron, :name string?}
- :crew (string)
- :model (string)
- :provider (string)
- :channel (string, nilable)
- :chatType (string, nilable) — camelCase preserved in storage today; coerce to :chat-type
- :cwd (string)

Lifecycle / timing:
- :createdAt (string ISO 8601) — camelCase preserved in storage today; coerce to :created-at
- :updated-at (string ISO 8601)

Compaction state:
- :compaction-count (int)
- :compaction-disabled (boolean)
- :compaction (map) — {:consecutive-failures int}

Token bookkeeping:
- :input-tokens (int, additive via update-tokens!)
- :output-tokens (int, additive)
- :total-tokens (int, derived sum)
- :last-input-tokens (int, last-write-wins)
- :cache-read (int, additive)
- :cache-write (int, additive)

The schema must be **read-permissive, write-strict**: a coercer normalizes legacy camelCase keys (:createdAt → :created-at, :chatType → :chat-type) on read, but writes only emit kebab-case. This is the migration path for existing on-disk data — no big-bang rewrite needed.

## Tasks

The worker tackles these in order within this single bead. Each is independently shippable as its own commit; the bead closes when all are done and acceptance criteria pass.

### 1. Drop legacy key format
- Delete build-key, build-thread-key, parse-key from isaac.session.key.
- Tear out internal usage in storage.clj: legacy-key?, session-id, slugify-based-on-conversation, entry-defaults legacy parsing.
- entry-defaults pulls crew/channel/chatType from opts only.
- Delete spec/isaac/session/key_spec.clj (build-thread-key is spec-only confirmed).

### 2. Lift naming strategy to isaac.session.naming
- Multimethod keyed on strategy (:sequential, :adjective-noun).
- Move random-name, next-sequential-name, generated-name, naming-strategy out of storage.clj.
- SessionStore impls call (naming/generate strategy state).

### 3. Define isaac.session.schema/Session
- c3kit.apron.schema covering the 22 fields above.
- Read-permissive coercer (camelCase → kebab-case for :createdAt and :chatType).
- Write-strict — schema/conform! on all writes.
- Replace storage.clj's session-entry-keys list and normalize-session-entry-keys reduce.

### 4. Define SessionStore protocol + transitional pass-through
- New ns isaac.session.store (the protocol). Existing isaac.session.store renames to isaac.session.store.memory.
- isaac.session.storage public fns become thin pass-throughs:
  `(defn append-message! [state-dir name msg] (store/append-message! (file-store state-dir) name msg))`
  This is the transitional state. Old call sites keep working unchanged.
- file-store is a record-or-fn that wraps state-dir.

### 5. Implement FileSessionStore
- isaac.session.store.file/->FileSessionStore [state-dir naming-strategy-key].
- Preserves today's index.edn behavior under the protocol surface.
- All schema conform! happens here on write.

### 6. Migrate callers, cluster by cluster

Per-cluster usage map (verified by grep). Each cluster lands as its own commit — no separate beads filed.

**6a — Session-internal (2 files, 5 fns).** Lowest risk; proves the protocol on its own callers.
- src/isaac/session/cli.clj         — delete-session!, get-session, list-sessions
- src/isaac/session/compaction.clj  — get-session, get-transcript, splice-compaction!, update-session!

**6b — Tools (2 files, 2 fns).** Tiny.
- src/isaac/tool/builtin.clj        — get-session, update-session!
- src/isaac/tool/memory.clj         — get-session

**6c — Cron + hooks (2 files, 2 fns).** Tiny.
- src/isaac/cron/scheduler.clj      — create-session!
- src/isaac/server/hooks.clj        — create-session!, get-session

**6d — ACP layer (3 files, 4 fns).**
- src/isaac/comm/acp/cli.clj        — get-session, get-transcript, list-sessions
- src/isaac/comm/acp/server.clj     — create-session!, get-session, get-transcript, open-session
- src/isaac/comm/acp/websocket.clj  — get-session, list-sessions

**6e — Bridges + api (3 files, 5 fns).**
- src/isaac/api.clj                 — create-session!, get-session
- src/isaac/bridge.clj              — get-session, get-transcript, update-session!
- src/isaac/bridge/prompt_cli.clj   — create-session!, most-recent-session, open-session

**6f — Drive (1 file, 6 fns).** Heaviest writer; do last when confidence is highest.
- src/isaac/drive/turn.clj          — append-error!, append-message!, get-session, get-transcript, update-session!, update-tokens!

Recommended order: 6a → 6b → 6c → 6d → 6e → 6f. Each cluster constructs a SessionStore at the appropriate seam (typically the bridge/server/scheduler layer) and threads it through; the in-cluster file uses (store/op store name ...) instead of (storage/op state-dir name ...).

Specs migrate alongside their src cluster; spec callers (21 files) use MemorySessionStore.

### 7. Retire isaac.session.storage façade
- After 6f closes, the pass-throughs in storage.clj have no callers. Delete the namespace.
- The remaining helpers either move to FileSessionStore as private or die.

### 8. Undefer isaac-kf7q
- `bd update isaac-kf7q --status=open` (and remove the dolt defer).
- Sidecar refactor becomes a single-file rewrite of FileSessionStore — no callers affected.

## Out of scope

- Changing the on-disk format (sidecar vs index — that's isaac-kf7q).
- Threading semantics (build-thread-key is spec-only, deleted in task 1).
- Concurrency model (each impl handles internally).
- session.compaction / session.context / session.logging stay where they are.

## Acceptance Criteria

bb spec green; bb features green throughout; isaac.session.key contains no build-key, build-thread-key, parse-key; isaac.session.storage namespace deleted (after sub-bead 7); isaac.session.store is the protocol ns; FileSessionStore (file impl) and MemorySessionStore (in-memory) both implement it; every src caller in clusters 6a-6f uses (store/op store name ...); session metadata writes go through schema/conform!; legacy camelCase keys (:createdAt, :chatType) coerce to kebab-case on read; isaac-kf7q undeferred.

## Design

One protocol over two protocols (SessionStore vs Session value): the codebase already uses the flat (op store key …) pattern. A typed Session value would be a bigger change for marginal ergonomics. Start flat. Naming strategy as multimethod over protocol-method: only two strategies, both stateless transformations, multimethod extends without protocol churn. Schema with c3kit consistent with rest of codebase (already used for compaction config and elsewhere). The protocol bead is the natural prerequisite for isaac-kf7q (sidecar) — file independently and run undeferral afterward.

## Notes

Verification failed: bb spec and bb features are green, and the legacy session-key code plus public storage facade are gone, but the session schema is still not wired into the file-backed store implementation. src/isaac/session/schema.clj defines conform-read and conform!, yet src/isaac/session/store/file_impl.clj still normalizes metadata with its own session-entry-keys / normalize-session-entry-keys helpers and does not call schema/conform! on writes or schema/conform-read on reads. That misses the bead acceptance requiring session metadata writes to go through schema/conform! and legacy camelCase keys to coerce through the read-permissive, write-strict schema path. The prior bridge_spec legacy-key follow-up appears resolved, but the schema integration acceptance is still unmet.

