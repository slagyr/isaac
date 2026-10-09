---
# isaac-e0t7
title: 'Session tags stored as a vector never match a selector: tags-of + contains? is index lookup'
status: completed
type: bug
priority: high
created_at: 2026-09-20T07:21:23Z
updated_at: 2026-10-07T18:58:47Z
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
on disk on zanebot and skiff.

## Design (planner, 2026-10-07)

The namespaces moved since this was written: `isaac.agent.session.store.spi`
(`tags-of`, `has-tag?`, `by-tags`) and `isaac.agent.frequencies`
(`session-matches?`, the one selector every band, comm and `prompt
--session-tag` goes through). The crew store keeps a parallel copy
(`isaac.agent.crew.store`).

**One normalizer, used on the way in and the way out.** `tags->set` in
`isaac.agent.session.store.spi`: nil → `#{}`; a set, vector, list or seq
→ the set of its members as keywords (strings become keywords; keywords
stay). Then:

- `tags-of` returns `(tags->set (:tags session))`, so `has-tag?` and
  `by-tags` match whatever shape is on the session or on disk. The crew
  store's copy uses the same function.
- `open-session!` normalizes `:tags` in both stores (`session/store/memory`,
  `session/store/impl_common create-session!`), and so does the
  frequencies create path (`frequencies.clj` `:session-tags` → `:tags`),
  which is how a band's `[:ops]` (the manifests allow a seq) became a
  session nobody could find.
- `sessions set <id>.tags <value>`: `conform-read` / the session schema
  coerce a seq to the set instead of refusing it; the refusal and its
  guidance stay for anything that is not a collection of keywords or
  strings. `.tags.<keyword>` add and unset are unchanged.
- Reading `session.edn` from disk (`impl_common` and `schema/conform-read`)
  normalizes too, so a session written as `:tags [:isaac]` is found at
  once and rewritten as a set on its next write. No migration.

**Not changed:** the selector side's own `normalize-tags` in frequencies
stays (it can share `tags->set`); the JSON output of `sessions show`
already renders a set as a list.

**After landing:** `sessions list` on zanebot and skiff with `--tag` for
the bands in use will say whether any session on disk was invisible; no
code is needed for them.

## Scenarios (@wip on module main)

- isaac-agent `features/tagging/session_tags.feature:140` sessions list
  --tag finds a session whose tags were written as a vector
- isaac-agent `features/session/mutation.feature:191` sessions set
  <id>.tags accepts a vector and stores a set
- isaac-agent `features/bridge/cli-prompt.feature:192` --session-tag
  selects a session whose tags were written as a vector
- isaac-hail `features/session-create.feature:122` a band whose
  session-tags are a vector reuses the session it created (the bug as it
  bites: the second hail used to create session-2)

No new steps; `the following sessions exist:` already EDN-reads a vector
cell. The hail scenario exercises the agent through hail's pinned
`isaac-agent` dep, so it passes only after the agent change lands and
hail's pin is bumped (the landing flow's "repin and re-`bb ci` any
downstream sibling"); gchat has the same dependency and the same fix,
with no scenario here (isaac-gchat is not in this workspace).

## Acceptance

- The four scenarios pass un-wipped in their modules; `bb ci` green in
  isaac-agent and in isaac-hail at the bumped pin.
- Unit specs on `tags->set` (nil, set, vector, strings, mixed) and on
  `has-tag?` over a vector-tagged session.
- No read of `:tags` anywhere in isaac-agent bypasses `tags-of` /
  `tags->set` (grep for `(:tags ` in `src/isaac/agent`).

feature-baseline: isaac-agent 4e235ae9d6cf7f046b1a3f39d80c62136ce8da28
feature-baseline: isaac-hail 0d1efa363c153fd4f7c6e29eb7bf4285949eade2
feature-blob: isaac-agent features/tagging/session_tags.feature 46d6f79d58fdb4624452b659b784851cf19469fc 140
feature-blob: isaac-agent features/session/mutation.feature cfaac0c8ca0d357c3eb257be29fd853bb63c2d39 191
feature-blob: isaac-agent features/bridge/cli-prompt.feature e9fac3d41e767626f10d9ab4c728a68a2a7115f5 192
feature-blob: isaac-hail features/session-create.feature f096166534831d722b604cd630c8db25fea724b0 122

## Landed on main (2026-10-07)

main-sha: isaac-agent 9c325bc5fdb08a5d3a136090176b1e4730741d40
main-sha: isaac-hail 498cb3516d2f6de4cd58403231f08c1d39b93e89

Pinning hail to current agent main also ran the in-flight scenarios
(`features/session-create.feature` "only matching session is in flight" and
`features/handoff.feature` "a busy target waits"). They expected `:held` and
got `:queued`: admission returned held without writing the record, so a turn
submitted `:queued` stayed `:queued`. That failed on agent `4e235ae` as well
as on the tags commit. `admit!` now writes `:state :held` for a busy session,
the same write the resource-pool hold path already did. `bb ci` is green in
both repos at these shas.

Skipped the post-landing `sessions list` on zanebot and yopp: no access to
those hosts. No code change is required for them.

## Authorship rewrite (planner, 2026-10-07)

Micah asked to drop the Cursor Agent identity. The two isaac-agent commits and the one
isaac-hail commit were rewritten to Zane (identical trees; hail re-pinned to the new agent
sha) and force-pushed with a lease. Old shas: agent 9641d7f/db59ff7, hail 58d9d4e.
