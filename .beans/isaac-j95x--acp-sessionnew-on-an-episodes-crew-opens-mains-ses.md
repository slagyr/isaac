---
# isaac-j95x
title: ACP session/new on an episodes crew opens main's session named "session" — --crew marvin lands the user on crew main
status: todo
type: bug
priority: high
created_at: 2026-09-24T20:41:09Z
updated_at: 2026-09-24T20:41:09Z
---

Micah, 2026-09-24: `toad acp "zane-isaac acp --crew marvin"` → "Why did I get main crew?" zanebot cli.log: argv `["acp" "--crew" "marvin"]`, then `:session/behavior-resolved :crew "main" :session "session"`; `~/.isaac/sessions/main/session` (created 2026-07-10) was updated by his turn.

## Cause (three layers)
1. `isaac.comm.acp.server/session-new-handler`: with no `:name` in params (Toad never sends one) it uses `(policy/default-session sess crew-id …)`. Marvin's crew is `:session-policy :episodes`; **`EpisodesPolicy/default-session` returns nil** (episodes.clj:243) — by design "a fresh id" — but the server then calls `open-acp-session! sess nil crew-id …`.
2. `EpisodesPolicy/open-session!` does `(session-id* name)` = `(str nil)` = `""` and `(or (store/get-session store "") (store/open-session! …))`; the sidecar store resolves that degenerate id to the existing session `session` (under crew main) and returns it — an existing session of ANOTHER crew, with `:crew "main"` kept.
3. The chronicle policy has the same shape of bug: `default-session` falls back to `most-recent-session` across ALL crews when the crew has none.

## Fix
- acp server: when `default-session` returns nil, mint a fresh session id (`policy/open-session!` with a generated name, e.g. `acp-<timestamp>-<rand>` or the episodes id scheme) — never call open-session! with nil.
- episodes + chronicle `open-session!`: opening an id that exists under a DIFFERENT crew than `:crew opts` is an error (or the acp server must check `(:crew existing)` = crew-id and refuse/mint). No silent cross-crew attach.
- chronicle `default-session`: drop the cross-crew `most-recent-session` fallback; nil when the crew has no sessions.
- sidecar `get-session ""`/`session-id ""` must not resolve to a real session.
- Scenario (isaac-acp): `isaac acp --crew marvin` on a root where marvin is an episodes crew and main has a session → session/new returns a NEW session whose crew is marvin; a second `session/new` returns another new one. Scenario (agent): chronicle default-session with no crew sessions → nil.

## Acceptance
- [ ] scenarios above green; `bb ci` green in isaac-acp, isaac-agent, isaac-episodes as touched; version bumps; registry repins.
- [ ] On zanebot: `zane-isaac acp --crew marvin` + session/new → behavior-resolved crew marvin.

Workaround until then: `zane-isaac acp --crew marvin --session <an existing marvin session id>` (attach path bypasses session/new's defaulting).

Repo scope: isaac-acp (server.clj), isaac-episodes (policy/episodes.clj), isaac-agent (policy/chronicle.clj, store/sidecar.clj).
