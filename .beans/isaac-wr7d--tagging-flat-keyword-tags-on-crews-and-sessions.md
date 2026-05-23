---
# isaac-wr7d
title: 'Tagging: flat keyword tags on crews and sessions'
status: draft
type: feature
created_at: 2026-05-23T02:50:45Z
updated_at: 2026-05-23T02:50:45Z
---

## Motivation

Hail's subscription matcher (isaac-ugx7 slice 2) needs to query tags
on crews and sessions to route hails. Tags are also useful on their
own for human discovery — `isaac crew list --tag role/worker`,
`isaac sessions list --tag project/chess`.

A single uniform tagging primitive keeps the mental model simple:
**flat sets of keyword tags, on both crews and sessions, matched by
set inclusion.** Tags can be plain (`:wip`, `:experimental`) or
namespaced (`:role/worker`, `:project/chess`). Namespacing is
optional and conventional — use it when the tag represents a
dimension (one value among many possible); plain when it's a label
(set membership).

## Scope

### Crew config: `:tags`

Add an optional `:tags` field to crew config — a set of keywords.
Defaults to `#{}` when absent.

```clojure
{:crews
 {:alice    {:tags #{:role/worker}        :model :sonnet-4-6 ...}
  :chess-bot {:tags #{:role/worker :project/chess} ...}
  :bob      {:tags #{:role/verify :wip} ...}}}
```

Schema: a set of keywords, namespaced or not. Validated at config
load. No registry, no normalization — operators are trusted to use
consistent vocabulary (a future `isaac tags list` surface can help
catch drift).

### Session metadata: `:tags`

Sessions gain a persisted `:tags` field in the session store. Set at
creation time; mutable post-creation via a future API (out of scope).

Tags are set by:

- **Hail's fan-out worker** when creating a new session for a
  matched subscription's `:session-tags` (isaac-ugx7).
- **CLI session-creation flags** — `isaac prompt --tag <k>`,
  `isaac chat --tag <k>` (repeatable). Tags carry forward to the
  session record.
- **ACP and HTTP session creation** — analogous `tags` field in
  the creation payload.

### Listing CLIs: filtering and display

`isaac crew list` and `isaac sessions list` both gain:

- **Display** — tags rendered in the listing (column or inline).
  JSON output (`--json`) includes a `:tags` field on each record.
- **Filter** — `--tag <k>` selects entities with that tag.
  Repeatable with AND semantics: `--tag role/worker --tag
  project/chess` requires both.
- **Composition** — `isaac sessions list --tag project/chess
  --not-in-flight` combines tag filters with isaac-a1nu's in-flight
  filters.

### Show CLIs: detail views

`isaac crew show <name>` and `isaac sessions show <id>` display tags
in their detail output (text and `--json`). Symmetric with the
listing surface.

### Crew tag mutation: `isaac config set / unset`

Crew tags live in config; mutated via the existing config CLI:

```
isaac config set   crew.joe.tags.wip
isaac config set   crew.joe.tags.role/worker
isaac config unset crew.joe.tags.wip
```

**Path parsing.** The path is `crew.<name>.tags.<keyword>`.
Everything after `tags.` parses as a single keyword, even when it
contains `/` for namespacing — so `tags.role/worker` →
`:role/worker`, not a nested path.

**Idempotency.** Setting an already-present tag is a no-op success.
Unsetting an absent tag is a no-op success. No errors; safe to
re-run in scripts.

Session tag mutation (`isaac sessions set/unset <id>.tags.<k>`) is
deferred to a follow-up bean — it's really a general
session-record mutation surface, of which tag mutation is one
application. v1 sessions are tagged only at creation (`--tag` flags
on prompt/chat/acp/http).

### Query helpers on stores

Minimal API on the crew and session stores:

- `(tags-of entity)` → set of keywords
- `(by-tags store tag-set)` → entities whose tags ⊇ tag-set
- `(has-tag? entity tag)` → boolean

Hail's matcher uses these directly.

## Out of scope (deferred)

- **Session tag mutation.** v1 sessions are tagged only at creation
  (via `--tag` on prompt/chat/acp/http or by Hail's fan-out
  worker). Post-creation mutation lives in a follow-up bean that
  adds general `isaac sessions set/unset <id>.<key>` mutation
  (tags being one application). That follow-up bean also owns the
  session-schema work needed to validate arbitrary field changes.
- **Tag registry / normalization.** No central list of "valid tags";
  no warning for `:chess` vs `:Chess` typos or `:project/chess` vs
  bare `:chess`. A future `isaac tags list` surface (introspectable
  registry of in-use tags) is a low-cost follow-up.
- **Tag inheritance.** Sessions don't inherit crew tags at the
  storage level. Hail's matcher unions them at query time; no need
  to persist inheritance.
- **Deletion safety.** Removing a tag that subscriptions match
  silently changes routing. Could warn at config-reload; future
  refinement.

## Acceptance

**Schema and storage:**

- Crew config schema accepts `:tags #{...}` (set of keywords),
  default `#{}`, and the meta-test for schema ownership passes.
- Session store persists `:tags` on session records; missing field
  treated as `#{}`.

**Creation surfaces:**

- `isaac prompt --tag <k>` (repeatable) tags the created session;
  same for `chat`. ACP and HTTP session-creation payloads accept a
  `tags` field.

**Listing surfaces:**

- `isaac crew list` and `isaac sessions list` display tags inline
  and include `:tags` in `--json` output.
- `--tag <k>` filters listings with AND semantics when repeated.

**Detail surfaces:**

- `isaac crew show <name>` and `isaac sessions show <id>` display
  tags in text and `--json` output.

**Crew mutation surface:**

- `isaac config set crew.<name>.tags.<keyword>` adds a tag (parses
  the keyword segment including any `/` for namespacing).
- `isaac config unset crew.<name>.tags.<keyword>` removes a tag.
- Both operations are idempotent (set-present / unset-absent are
  no-op successes).

**Query helpers:**

- `tags-of`, `by-tags`, `has-tag?` exposed on both stores.

**Feature scenarios under `features/tagging/`:**

- Crew config with `:tags #{:role/worker}` loads and round-trips.
- Session created with `--tag :project/chess` round-trips through
  the store.
- `isaac crew list --tag role/worker` shows only matching crews
  with their tags displayed.
- `isaac sessions list --tag project/chess --not-in-flight`
  intersects the two filters correctly.
- `isaac config set crew.joe.tags.wip` adds the tag; running it
  again is a no-op.
- `isaac config unset crew.joe.tags.wip` removes the tag; running
  it on an absent tag is a no-op.
- `isaac config set crew.joe.tags.role/worker` correctly stores
  `:role/worker` (namespaced) via the dotted path.
- Tags missing on older session records read as `#{}` without
  error.

## Relationship to other beans

- **Required by isaac-ugx7 (Hail).** Slice 2 (subscription matcher)
  reads tags from crews and sessions; can't ship without this bean.
- **Companion to isaac-a1nu (Crew concurrency).** The sessions CLI
  work overlaps — `--tag` and `--in-flight`/`--not-in-flight` are
  composable filters. Whichever ships second extends the filter set.
- **Followed by isaac-wirv (Session mutation CLI).** Carries the
  deferred session-tag mutation surface plus the session-schema
  mutability metadata it requires. Sessions can be tagged at
  creation in v1; post-creation tag mutation arrives with wirv.
- **Status:** draft — main open question is whether anything is
  missing from the listed surfaces. Cross-entity tag inheritance
  beyond Hail's match-time union is settled (Hail unions at query
  time; no persisted inheritance needed).
