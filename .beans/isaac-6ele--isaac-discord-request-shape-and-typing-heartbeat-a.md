---
# isaac-6ele
title: 'isaac-discord: request shape and typing heartbeat are store-agnostic'
status: completed
type: feature
priority: normal
created_at: 2026-09-09T16:35:02Z
updated_at: 2026-09-10T09:17:13Z
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


## Progress (2026-09-10, scrapper@isaac-work-1)

isaac-discord `bean/isaac-6ele` @ **5bbeb28** (base origin/main@3568cc5).

**Done:**
- `process-message!` always sets `:session-key` to the Discord session name; never `:conversation`. Dropped the `episodes-crew?` request-shape branch and the `isaac.episodes.lifecycle` require.
- `resolve-inbound-session!` always `ensure-session!` on create (episodes no longer skip open).
- Typing heartbeat starts on `on-turn-start` for every Discord session (store-agnostic); `on-cycle-start` only records origin channel-id.
- `features/comm/discord/episodes.feature` plants recut `:conversation` → `:session-policy`; assertions prove container open, warm append, stable session id (`discord-c999`).
- Pin isaac-agent **837b6d4** (0.1.58) in deps.edn + bb.edn.

**Green:**
- `ISAAC_GIT=1 clojure -M:features features/comm/discord/episodes.feature` 3/0
- typing.feature 4/0, routing.feature 9/0, scuttlebutt.feature 3/0
- `bb spec` 46/0; `bb jvm-spec` 97/0
- `grep -rn ':conversation\|isaac\.episodes' src` empty

**Handoff:** branch: bean/isaac-6ele @ 5bbeb28 (base origin/main@3568cc5).



## Verify fail (attempt 1, 2026-09-10): typing.feature hangs after 2 green examples; bb jvm-features gate times out

HEAD (beans repo): 3f5ab544f7099a5ee734478ff2700c2445005d75
Working tree: clean
isaac-discord: bean/isaac-6ele @ 5bbeb28baf20cf00c6c980a3f0778507557a9f4f (base origin/main 3568cc5)

Gate from bean (named runs, ISAAC_GIT=1):

- `grep -rn ':conversation\|isaac.episodes' src` empty. Pin 837b6d4 present in deps.edn + bb.edn.
- `bb spec` 46 examples, 0 failures, 96 assertions (0.54s).
- `clojure -M:features features/comm/discord/episodes.feature` 3/0/13 in 10.1s.
- `clojure -M:features features/comm/discord/routing.feature` 9/0/10 in 18.0s.
- `clojure -M:features features/comm/discord/scuttlebutt.feature` 3/0/6 in 9.1s.
- `clojure -M:features features/comm/discord/typing.feature` **hangs**. After ~25s stdout is two green dots (scenarios 1–2), then no further output through 180s. Process stays alive as gherclj.main. `bb jvm-features` wraps this in a 60s timeout and exits 124 (`jvm-features timed out after 60s`) even when the examples that did finish were green.

Repro (clean leftover gherclj first; ISAAC_GIT=1 so the pin is used, not ../isaac-agent):

    cd isaac-discord
    pkill -f 'gherclj.main' || true
    rm -rf target/gherclj/generated/
    export ISAAC_GIT=1
    clojure -M:features features/comm/discord/typing.feature

Hang is inside scenario 3 (`the heartbeat stops when the turn ends`) — two dots means scenarios 1–2 including after-hooks completed. Likely leftover grover wait / in-flight turn from scenario 2 (`wait: true`) so MESSAGE_CREATE or test-clock advance never returns. Check session-key case: episodes.feature asserts store id `discord-c999` while the Discord step parks `waiting-session*` as `discord-C999`; `grover/release-wait!` on the wrong key is a no-op and the next scenario blocks.

Do not land. Fix typing.feature so the named `bb jvm-features features/comm/discord/typing.feature` run exits 0 with 4 examples / 0 failures inside the 60s bb timeout, then re-run the other named gates.


## Progress (2026-09-10, scrapper@isaac-work-1, verify-fail repair)

isaac-discord `bean/isaac-6ele` @ **97e9763** (base origin/main@3568cc5).

**Cause:** typing.feature scenario 2 parks a Grover `wait: true` turn on a
`clojure.core/future` (non-daemon agent send-off pool). Discord steps parked
`waiting-session*` as `discord-C999` while Grover wait-gates use the charge
session-key; `session_steps/-drain-parked-turn!` only sees `g :turn-future`,
then `grover/reset-queue!` drops the promise without delivering it.
`maybe-wait!` spun forever. The JVM printed 2 (or later 4) green dots then
never exited — `bb jvm-features` 60s wrapper exit 124.

**Fix (spec/isaac/comm/discord/discord_steps.clj only):**
- Drain every Grover wait-gate + both casings (`discord-C999` / `discord-c999`).
- `bridge-cancel/cancel!` after session_steps `clear!`, then join the future,
  `clear-in-flight!`, `bridge-cancel/clear!`.
- `g/after-all` `shutdown-agents` so the JVM actually exits.

**Green (ISAAC_GIT=1):**
- `clojure -M:features features/comm/discord/typing.feature` 4/0/4 in 10.2s, process exit 0 in 21.7s
- `bb jvm-features features/comm/discord/typing.feature` 4/0, exit 0 in 22s (under 60s)
- episodes.feature 3/0/13; routing.feature 9/0/10; scuttlebutt.feature 3/0/6
- `bb spec` 46/0/96
- `grep -rn ':conversation\|isaac.episodes' src` empty

**Handoff:** branch: bean/isaac-6ele @ 97e9763 (base origin/main@3568cc5).



## Landed on main (2026-09-10)

main-sha: isaac-discord 6ffa54164675f0ea3b4739ed525ec40c1a6e6174

## Deploy note (2026-09-10 09:16Z)

Shipped on one train: isaac-agent 0.1.58 (837b6d4) + isaac-discord 0.1.14 (40da00e), registry a1346660. zanebot crew files marvin.edn and pilot.edn re-keyed `:conversation :episodes` → `:session-policy :episodes` (zane-isaac commit) in the same breath as the restart. Boot clean: no post-boot errors or warnings, `:isaac.agent/session-policy` berth registered chronicle + episodes, Discord gateway ready at 09:16:00Z, b6w0 worker resumed on isaac-work-2. Downstream suites (hail, server, acp, claude, cron, cli-proxy) were green locally against 0.1.58 before the pin.
