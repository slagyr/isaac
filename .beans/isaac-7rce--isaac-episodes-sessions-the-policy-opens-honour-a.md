---
# isaac-7rce
title: 'isaac-episodes: sessions the policy opens honour a requested name and otherwise get the agent''s adjective-noun names'
status: in-progress
type: feature
priority: high
tags:
    - episodes
    - agent
    - unverified
created_at: 2026-09-20T00:06:00Z
updated_at: 2026-09-20T05:20:37Z
blocked_by:
    - isaac-8s6s
---

Micah 2026-09-19: episode sessions on yopp are named by episode timestamp (20260918025608859) while chronicle sessions get the agent's star-themed adjective-noun names (clever-dove). Both should behave the same: when a comm or CLI opens a session with an explicit name/identifier (e.g. gchat's canonical per-space session, --session foo), the episodes policy keeps that as the session id; when none is given it asks the agent's naming strategy (isaac.session.store.spi/make-naming-strategy — adjective-noun by default, sequential when configured) instead of minting a timestamp. Episode ids stay timestamps; it is the SESSION id that changes. Scenarios (episodes features): open with a name → session id is the name; open without → adjective-noun; recall/lineage unaffected.



**Re-scoped 2026-09-19 (Micah):** the AGENT owns session naming. Episode ids stay timestamps; the backing session is named by the agent's naming strategy before any policy's open-session! is called, and a policy takes the identifier it is given. Today isaac-episodes/lifecycle.clj falls back to the episode id when no :session-id is supplied — that fallback goes, and the agent's api/open-session! (and every path that opens through a policy: comms, CLI, hail) mints a name when the caller has none. Work is mostly in isaac-agent; episodes loses its fallback.

## Handoff (2026-09-20)

Branches (verify lands them; I did not merge or pin):
- isaac-agent: `bean/isaac-7rce` @ `c24a179` (base origin/main@`20660e6`)
- isaac-episodes: `bean/isaac-7rce` @ `d1b8768` (base origin/main@`38409b1`)

**Agent (owns naming).** New `isaac.session.store.spi/mint-name` ([] / [root])
returns a name from the configured naming strategy (adjective-noun by default,
sequential when configured). `isaac.api/create-session!` mints when the caller
passes no identifier, before the policy is called; `bridge/prompt-cli`
`ensure-session!` mints when neither the caller nor the policy's
`default-session` supplies an id, so the session is opened THROUGH the policy
with a real name instead of falling through to the store. A policy now only
ever receives an identifier it was handed.

**Episodes (loses its fallback).** `EpisodesPolicy/default-session` no longer
mints a timestamped id — it answers nil; naming is the agent's job.
`lifecycle/open-episode!` names the backing session by the session id
(`:thread`), never by the episode id; with no session id it opens no session
and warns `:episodes/open-without-session-id`. `resolve-thread!`,
`chain-successor!` and the `:reused` branch of `compact-close!` return the
session id as `:session-key` (they returned the episode id). Episode ids stay
`yyyyMMddHHmmssSSS`.

Acceptance:
- isaac-agent `features/session/session_policy.feature`: "a start the policy has
  no default for is named by the agent, not the policy" — sequential strategy,
  logbook policy records `default-session` (no id) then `open-session!
  session-1`, `record-turn-marker!`/`append-message!` on `session-1`.
- isaac-episodes `features/episodes/session_naming.feature` (new): `--session
  reef-chat` keeps `reef-chat` as the session id with a timestamp episode id;
  no `--session` gives `session-1` (strategy) with the episode's `:session-id`
  pointing at it.
- Specs: `isaac.api-spec` (mints when unnamed; explicit name wins),
  `isaac.session.policy.episodes-spec` (no default session; keeps the id it is
  handed, episode id stays a timestamp), `isaac.episodes.lifecycle-spec`
  (backing session named by the session id; no session when none is named).

Runs: isaac-agent `bb spec` 1654 examples / 0 failures, `bb features` 832 / 0
(1 pre-existing pending), `bb config-bypass-lint` + `bb lint-cli-host` ok.
isaac-episodes `bb spec` 216 / 0, `bb features` 85 / 0 against the CURRENTLY
PINNED agent (the episodes branch is green without the agent change; the agent
change is what routes the minted name through the policy).

Ordering note: land isaac-agent first. The episodes suite is green on the old
pin, so no pin bump is required to land, but the agent pin should be bumped on
the usual cadence for the policy to see agent-minted names.

Cross-repo check with a `:local/root` override of isaac-agent showed 23-24
pre-existing failures in the episodes feature suite against agent main — drift
between agent main and the episodes pin (recall/index and provider-attention
scenarios), present with and without this bean's changes. Not touched here; the
override was reverted and is not committed.

Observation for the planner: `blocked_by: isaac-8s6s` on this bean looks stale.
Commit dd2f1ad7 ("swap crossed re-scope notes (bklu/7rce), record blockers")
moved the people-index note (which carried the dependency) to isaac-bklu and
added the blocker there; this bean kept the copy. Session naming has no
dependency on the google people index. Left in place — clearing it is a
planner edit.
