---
# isaac-6ele
title: 'isaac-discord: request shape and typing heartbeat are store-agnostic'
status: todo
type: task
priority: normal
created_at: 2026-09-09T16:35:02Z
updated_at: 2026-09-10T06:27:56Z
blocked_by:
    - isaac-mmod
---

Repo: isaac-discord. Blocked by isaac-mmod. `discord.clj` builds `:conversation {:kind :thread …}` for episode crews and `:session-key` otherwise (lines ~375–398) — collapses to `:session-key` always; the typing heartbeat currently starts only for episode sessions (first cycle) — per Micah (2026-09-09) it runs regardless of the session store. Remove the `isaac.episodes.lifecycle` require. Acceptance: Discord features (`routing.feature`, `scuttlebutt.feature`, typing scenarios) green; the typing scenario's crew no longer needs to be an episodes crew (rewrite that Given to a chronicle crew, @wip first); `grep -rn 'isaac\.episodes' src` empty.

## Merged from isaac-ru3e (planner-crew draft, 2026-09-10; ru3e scrapped as a duplicate)

mmod landed as isaac-agent 0.1.58 (**837b6d4**, squash 5104ad4). Bridge dispatch now requires `:session-key`; the crew config key is `:session-policy :chronicle | :episodes` (`:conversation` is gone). Against that agent `features/comm/discord/episodes.feature` is red even with the plant lines re-keyed: `process-message!` omits `:session-key` for episode crews (it sends `:conversation {:kind :thread ...}`), so no session is opened and the episodes policy never opens a container. This is Discord request-shape, not an agent bug. Do not recut Discord inside isaac-agent.

Required (in addition to the above):
1. `process-message!` always sets `:session-key` to the Discord session name (channel/thread identity). Never put `:conversation` on the charge. Drop the episode?/chronicle request-shape branch; the crew's policy is the agent's problem.
2. Re-key the plant lines in `features/comm/discord/episodes.feature` (`| conversation | episodes |` → `| session-policy | episodes |`, two scenarios, plus the prose line). Recut assertions only as needed so they prove: an episodes crew opens a container on first message; a warm second message appends to the same session id; the session id never changes.
3. Pin isaac-agent **837b6d4** in deps.edn (and the spec dep).

Acceptance:

    cd isaac-discord
    bb jvm-features features/comm/discord/episodes.feature
    bb jvm-features features/comm/discord/typing.feature features/comm/discord/routing.feature features/comm/discord/scuttlebutt.feature
    bb spec

0 failures; `grep -rn ':conversation\|isaac\.episodes' src` empty. The full `bb features` wrapper may hit its timeout; the named runs are the gate.

Deploy note: agent 0.1.58 is held off zanebot until this lands; both ship on one train with marvin's crew file re-keyed.
