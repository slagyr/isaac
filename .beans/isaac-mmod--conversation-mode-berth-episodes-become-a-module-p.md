---
# isaac-mmod
title: 'Session-store berth: chronicle and episodes are session-store implementations selected per crew; the bridge stops resolving episodes'
status: todo
type: feature
priority: high
created_at: 2026-09-09T14:52:09Z
updated_at: 2026-09-09T16:35:02Z
---

Repo: isaac-agent. First of three beans to extract episodes+recall into a module (berth → extraction → train). Planning session 2026-09-09 (planner + Micah).

## Investigation (read-only, verified)
- Seam today: `isaac.conversation.router` (40 lines): protocol + multimethod `route-by-mode` on `[(:conversation crew-cfg) (:kind conversation)]` with hard-coded `[:episodes :thread]` and `[:chronicles :thread]` cases. Chronicle has NO dedicated code — it is the plain session path.
- Bypasses of the seam (direct requires of episode namespaces): `bridge/core.clj` (episodes-crew? → resolve-thread! → maybe-recall-at-open! in ensure-session!; maybe-seal! in maybe-live-seal!, skipped for :cli origins), `bridge/prompt_cli.clj` (its OWN copy of ensure-session! + a maybe-seal! at the end of run), `session/compaction.clj` (compact-close! + episode-store reads to splice), `drive/turn.clj` successor-session-key (episode-store reads), `comm/delivery/worker.clj` (episodes.worker start!/stop!), `tool/builtin.clj` (recall__search/scene hard-listed), `agent/module.clj` (requires the episodes/recall/embed CLI namespaces).
- Berths that already exist and cover the rest: `:isaac.agent/tools`, `:isaac/cli`, `:isaac.config/check` (check_contributions — the embedding-provider check is one). The idle-seal worker already registers its tick on the shared scheduler from start!.
- Episodes+recall: 15 src files (~3,100 lines), 8 feature files, 20 steps (spec/isaac/episodes/episode_steps.clj), config keys :episodes/:embedding/:recall. No third-party libs beyond cheshire/clj-yaml.

## Decisions (2026-09-09, Micah)
1. **One berth, one protocol.** `:isaac.agent/conversation` contributes conversation modes keyed by mode keyword; each is a `ConversationMode` (protocol) with hooks `route`, `open!`, `seal!`, `compact-close!`, `successor`; modules `extend` + merge a defaults map (the Comm pattern). Chronicle = the agent's defaults (route by conversation id; open! ensures the session exists; seal!/compact-close!/successor no-ops).
2. **Default vs unknown.** A crew with no `:conversation` is a chronicle. A crew naming a mode no module provides is a config VALIDATION ERROR (via a `:isaac.config/check` contribution), like a bad model alias.
4. **Spelling: `:chronicle`**, singular, clean cutover (the router's `:chronicles` goes; no alias).
5. **Fixture:** the seam is proven with a fictional mode `logbook` (Marigold cast) implemented in spec support that records every hook call; `features/bridge/episode_dispatch.feature` is rewritten against it; episodes' own features move with the module in the extraction bean.
3. OPEN — the prompt CLI's duplicated open/seal copy (see chat).



## Decisions recut (2026-09-09, Micah) — supersede decisions 1 and 3 above
- **Vocabulary**: `session` is the surface's identity inside AND outside (decision 33's boundary translation is retired; `thread` is retired). Under chronicle, session ≡ chronicle. Under episodes, a session id names a chain of episodes (episode record `:thread` → `:session-id`; successors still link by `:parent-episode`). Arcs (topic chains across scenes) stay phase-3, after the split.
- **The berth IS the session store.** `isaac.session.store.spi/SessionStore` (21 methods, keyed by session id) is the abstraction; a crew selects an implementation with `:session-store :chronicle | :episodes` (config key `:conversation` and the word 'mode' are gone). Berth `:isaac.agent/session-store`: a module contributes named implementations; chronicle is the agent's own (today's store, untouched); absent → chronicle; unknown name → config validation error (`:isaac.config/check` contribution).
- **The session id never changes.** The store maps it to its transcript internally (chronicle: current.ednl + rotation; episodes: the open episode's backing transcript). The bridge, drive, comms, hail and tools stop resolving episodes: bridge/core + prompt_cli lose `episodes-crew?/resolve-thread!/maybe-recall-at-open!/maybe-seal!`; drive/turn loses successor-session-key; session/compaction loses compact-close! (it calls splice-compaction!/append-compaction! and the store decides).
- **Episode behaviours live inside the episodes store**: recall at open in the first append-message! on a cold session; live seal on clear-turn-marker! (the bridge already records/clears markers for every dispatch, CLI included); close + successor on the compaction methods; idle seal as the module's scheduler task.
- **Protocol additions are fine** (Micah): add a `repair-transcript!` (what bridge/resume does with raw files today) and whatever operator-read method `sessions show/status` need, OR keep those as chronicle's CLI lens with `isaac episodes` as episodes' lens — worker's call, recorded on the bean. migrate stays chronicle-internal.
- **Discord heartbeat is store-agnostic** (Micah): the typing heartbeat runs regardless of the session store; Discord's episode/chronicle request-shape branch collapses to a plain session id. ACP's fresh-mint (session/new) and replay-open-episode (session/load) branches collapse to open-session!/active-transcript. Those two module changes are follow-up beans in isaac-discord / isaac-acp, dispatched after this bean's train.
- **Fixture**: the seam is proven with a fictional session store `logbook` (Marigold) in spec support that records every protocol call; `features/bridge/episode_dispatch.feature` is rewritten against it; the episodes features move with the extraction bean.



## Decisions (2026-09-09, Micah) — recall placement and default session
- **Recall is store content, not an API.** The episodes store's first `append-message!` on a cold session id opens the episode, runs recall on that message's text, appends the recalled blocks, THEN the message — so the prompt builder reads history → recalled blocks → live message (decision 1's cache order). `active-transcript` = what the session currently shows (open episode, or nothing when cold — ACP session/load replays exactly that); `get-transcript` = the full record (operator lenses). Prior episodes are never replayed (51xy decision 2); they come back only as recalled scenes.
- **Default session is the store's answer.** New protocol method (name: `default-session` [store crew opts]): when a conversation starts with no session id (prompt `--crew`, ACP session/new without a name, comm bindings without a session), the caller asks the crew's store. Chronicle → the crew's most recent session, creating it if none. Episodes → a fresh id (a new chain on first append). An explicit id is honoured by both; a cold explicit id on episodes opens a new episode on that chain. `--create` keeps its chronicle meaning and is a no-op for episodes (isaac-6yg0 stands). This removes the mode branch from session/frequencies and ACP's session-new.

## Scenario plan (approved titles; gherkin follows one at a time)
features/session/session_store.feature (new; bridge/episode_dispatch.feature deleted):
1. a crew selects a session store by name
2. a crew with no session-store setting is a chronicle
3. an unknown session store fails config validation
4. the session id is stable across compaction
5. turn markers are the store's turn signals, for the CLI too
6. an episodes crew keeps its session id through a cold open (rewrite of the @wip episode_dispatch scenario)
7. a warm second turn on an episodes crew appends to the open episode (rewrite)
8. a conversation start without a session id asks the store for one (logbook answers; frequencies and ACP no longer branch on mode)
Fixture: `logbook` = a recording decorator over the chronicle store (so every existing sessions/transcript step keeps working). New steps: `Given a recording session store "logbook" is registered`, `Then the logbook store recorded calls matching:` (table: method | session-id | …).



## Planted (2026-09-09) — isaac-agent main ddb9d7f
`features/session/session_store.feature` (8 @wip scenarios; `features/bridge/episode_dispatch.feature` deleted, its two @wip scenarios rewritten as 6 and 7).

## Step ledger
| Step | Status |
|---|---|
| default Grover setup / the isaac EDN file … exists with: / the following model responses are queued: / a charge is dispatched with: / the following sessions exist: / session … has transcript: / has transcript matching: / has compaction / the following sessions match: / an episode exists for crew … matching: / crew … has N episode(s) / the current time is / the user sends … / isaac is run with … / the exit code is / the stdout contains / the config has validation errors matching: | existing |
| **Given a recording session store "logbook" is registered** | **NEW** — spec-support decorator over the chronicle store, registered under the berth name; records every protocol call |
| **Then the logbook store recorded calls matching:** (columns: method, session-id, crew) | **NEW** — the only way to assert the protocol call sequence and ids |
| **Then the logbook store recorded no calls** | **NEW** — negative form |
| `an episode exists for crew … matching:` reads `session-id` (was `thread`) | existing step, record key renamed |

## Implementation surface (all isaac-agent)
1. Berth `:isaac.agent/session-store` in the manifest: named factories → SessionStore implementations; chronicle = today's store registered under `:chronicle`; episodes registered under `:episodes` (stays in the agent until the extraction bean).
2. Crew config `:session-store` (schema + `:isaac.config/check` contribution: unknown name → `references undefined session store (got "x"); known: …`). `:conversation` key removed (clean cutover; zanebot crews carry no :conversation today except marvin — the train re-keys `marvin.edn` `:conversation :episodes` → `:session-store :episodes`).
3. Store selection per crew at the seams that fetch a store (`nexus [:sessions :store]` users): the bridge/drive/comms/hail/tools obtain the crew's store; session id never rewritten.
4. Protocol additions: `default-session [store crew opts]`; `repair-transcript!` (what bridge/resume does with raw files). Route bridge/status, bridge/resume, session/context, session/cli through the protocol or scope them to chronicle (worker's call, record it).
5. Remove from callers: bridge/core + prompt_cli (`episodes-crew?`, `resolve-thread!`, `maybe-recall-at-open!`, `maybe-seal!`, the :cli seal exemption); drive/turn `successor-session-key`; session/compaction `compact-close!` + episode-store reads; session/frequencies mode branch (asks `default-session`).
6. Episodes store implementation: cold first append opens + recalls (blocks before the message); `clear-turn-marker!` seals; `splice-compaction!` closes + chains a successor; `active-transcript` = open episode or empty; `get-transcript` = full record; episode record `:thread` → `:session-id` (clean cutover, migrate existing records on read or via `isaac episodes migrate`).
7. Discord/ACP branches are NOT in this bean (follow-ups isaac-… below); their features must stay green against the agent seam.

## Acceptance
```
cd isaac-agent
bb features features/session/session_store.feature:20
bb features features/session/session_store.feature:48
bb features features/session/session_store.feature:68
bb features features/session/session_store.feature:80
bb features features/session/session_store.feature:117
bb features features/session/session_store.feature:141
bb features features/session/session_store.feature:169
bb features features/session/session_store.feature:204
bb features features/bridge/suspend.feature
bb features features/episodes/ features/recall/ features/comm/acp/ 2>/dev/null; bb features && bb spec
```
- All 8 green with @wip removed; suspend.feature unchanged and green with resume going through the protocol; the episodes/recall features green with :session-id.
- `grep -rn ':conversation' src spec features` empty; `grep -rn 'episodes-crew?\|resolve-thread!\|maybe-recall-at-open!\|maybe-seal!\|compact-close!\|successor-session-key' src/isaac/bridge src/isaac/drive src/isaac/session src/isaac/comm` empty.
- Downstream (isaac-acp `features/comm/acp/episodes.feature`, isaac-discord features) still green against this agent SHA — the verifier runs them with the pinned sibling.
- Train note: zanebot `crew/marvin.edn` `:conversation :episodes` → `:session-store :episodes` at deploy.
