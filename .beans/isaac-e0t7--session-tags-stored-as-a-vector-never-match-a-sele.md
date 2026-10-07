---
# isaac-e0t7
title: 'Session tags stored as a vector never match a selector: tags-of + contains? is index lookup'
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-09-20T07:21:23Z
updated_at: 2026-10-07T16:57:39Z
---

`isaac.session.store.spi/tags-of` returns whatever is on the session and
`has-tag?` calls `contains?` on it. With a set (`#{:ops}`) that is membership;
with a **vector** (`[:ops]`) `contains?` asks whether index `:ops` exists —
always false. A session whose tags were written as a vector therefore matches
no tag selector at all: not hail, not `isaac.session.frequencies`, not the
gchat space entries added by isaac-tund. It fails silently — the selector
simply finds nothing and falls back to creating or to a default.

Found 2026-09-20 writing isaac-tund's scenarios: `| ops-room | main | [:ops] |`
created a session with vector tags and the selector never saw it; `#{:ops}`
worked. Both spellings are accepted on the way in, so nothing warns.

This is the same sharp edge as [[config-set-member-paths]] — Micah hit it
before from the CLI side ("sessions set .tags can't take a set, edit
session.edn by hand"). A session on disk with `:tags [:isaac]` is invisible to
every band.

Work: normalize tags to a set of keywords wherever they are read (`tags-of`)
and wherever they are written (`open-session!`, `sessions set`, the feature
steps' session table). Scenario: a session created with vector tags is
selected by `--tags`, by hail, and by a gchat space entry. Then check what is
on disk on zanebot and yopp.


## Worker handoff (2026-10-07) — unverified

Ungated (no `feature-baseline:`). Status stays `in-progress`. Tagged `unverified` for `/verify`. Not completed. No verify hail: this session was not a hail delivery, so there is no reply-to id, and `isaac hail send` would be a real fleet send.

### Fix — isaac-agent only

`isaac.agent.session.store.spi/tags-of` (the bean's `isaac.session.store.spi` name is stale) returned `:tags` raw, and `has-tag?` calls `contains?` on it. On a vector, `contains?` is an index lookup, so `[:ops]` never matched.

`normalize-tags` coerces `nil`, a set, or a sequential collection of keywords, symbols, or names into a set of keywords. A non-collection (`"not-a-set"`) is left unchanged so schema still rejects it. `tags-of` runs that on read and returns `#{}` when the result is not a set, so a sidecar that still holds `[:ops]` is selected without rewriting the file.

Writes coerce the same way: memory and sidecar `open-session!` / `update-session!`, schema `conform-read` (so `sessions set <id>.tags [:ops]` passes "must be a set of keywords" and stores `#{:ops}`), the feature session table, CLI `--edn`/`--json` and bridge status. The CLI flag is `--tag` (repeatable), not `--tags`.

Hail and gchat were not changed. Both select through `isaac.agent.frequencies/session-matches?` → `has-tag?`. A new scenario in those repos would be red against the published agent pin, and an ungated worker must not pin a bean-branch sha. The shared seam is covered by `frequencies_spec` (`[:ops]` matches `#{:ops}`).

### Where the code is

Local commit `777dae9e1df74a77d1878b173b9eddfcf89f392f` on `bean/isaac-e0t7` in the isaac-agent checkout (parent `origin/main` `5b9a667`). Push to `github.com/slagyr/isaac-agent` was denied: `Permission to slagyr/isaac-agent.git denied to cursor[bot]` (403), including with this workspace's token. The commit is only on that VM checkout.

The same commit is `doc/isaac-e0t7-session-tags.patch` in this repo (`git apply --check` against isaac-agent `main` at `5b9a667` succeeded). Apply it on an isaac-agent branch Micah can push; do not pin `modules.edn` at `777dae9` — that sha is not on agent `main`.

### Tests (isaac-agent, commit 777dae9)

- `bb lint` on the changed `src/` files: 0 errors (pre-existing unused-binding warnings only).
- `bb spec`: 1904 examples, 0 failures, 3961 assertions.
- `bb features`: 881 examples, 0 failures, 2148 assertions, 1 pending. The pending scenario is pre-existing ("Mid-turn compaction keeps the request in flight").
- New scenarios: `features/tagging/session_tags.feature` "a session created with vector tags is selected by --tag"; `features/session/mutation.feature` "isaac sessions set <id>.tags stores a vector of keywords as a set".

### Disk check skipped

No access to zanebot or yopp from this session, so the on-disk glance did not happen. After this agent build, a session whose `session.edn` still has `:tags [:isaac]` (or any vector of keywords or names) matches `--tag`, hail selectors, and gchat space `session-tags` without rewriting the file. `sessions set <id>.tags [:isaac]` rewrites that field to `#{:isaac}`. Micah should glance at the live sidecars if the files themselves should be normalized.
