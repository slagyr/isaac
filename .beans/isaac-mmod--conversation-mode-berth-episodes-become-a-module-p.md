---
# isaac-mmod
title: 'Session policy berth over a primitive session store: chronicle and episodes are per-crew policies; the bridge stops resolving episodes'
status: in-progress
type: feature
priority: high
tags:
    - unverified
created_at: 2026-09-09T14:52:09Z
updated_at: 2026-09-10T06:15:52Z
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
- Downstream (isaac-acp `features/comm/acp/episodes.feature`, isaac-discord features) still green against this agent SHA — the verifier runs them with the pinned sibling. **Clarified after the first verify bounce (planner, 2026-09-10):** the Discord episodes feature plants `:conversation | episodes`; that plant line is config ripple from the rename, not Discord production code (Discord calls `lifecycle/episodes-crew?`, which now reads `:session-policy`). The verifier re-keys that one plant line to `:session-policy | episodes` in its local isaac-discord checkout before running; green with that one-line change counts. The re-key lands in isaac-discord on the deploy train (planner).
- Train note: zanebot `crew/marvin.edn` `:conversation :episodes` → `:session-store :episodes` at deploy.



## CONTRACT RECUT (2026-09-09 17:30Z, Micah) — supersedes 'the berth IS the session store'
The worker's first turn (hail e7e9b9c7, isaac-work-2) was cancelled at 17:28Z by restarting the server with its turn marker removed; discard any branch work from it — the contract below replaces the earlier one. Config key/berth names in the planted feature change accordingly (planner updates the feature file).

**Two protocols, not one.**
1. **Session store = persistence primitives**, chosen once per root (disk sidecar, memory; a database later), addressed by ids, never by path:
   - session record — session id (identity + overrides + `:session-policy`)
   - transcript stream, append-only — session id + container id
   - container record — session id + container id (episode.edn: status, timestamps, counters, parent)
   - container documents — session id + container id + name (scenes, gists)
   - crew documents — crew + name (the recall index + vectors: derived, module-owned, stored opaquely)
   - the sessions index (the store's own concern; id → crew/policy/updated-at; derived, rebuildable)
   The disk store's representation of these is isaac-b6w0's layout (`sessions/<crew>/<sid>/…`, `episodes/<cid>/…`, `sessions/<crew>/recall/`). The store never knows what an episode is; 'episodes' is the container primitive with more than one container per session.
2. **Session policy = what the bridge/drive/comms/tools talk to** (today's SPI surface: open/get/list/transcripts/append/compaction/markers + `default-session`), implemented per policy ON TOP of the primitives: **chronicle** = one container per session (today's behaviour, moved behind the primitives); **episodes** = open a container on a cold first append (recall injected ahead of the message), seal on clear-turn-marker! (scene documents + container record), close + successor container on the compaction methods, maintain the crew's recall documents. Policies never see a directory.
   Crew key `:session-policy :chronicle | :episodes` (absent = chronicle; unknown = config validation error); berth `:isaac.agent/session-policy` contributes named policy factories that receive the root store. Session ids are unique fleet-wide and never change.

**Ripple**: the current `isaac.session.store.spi/SessionStore` splits into the primitives protocol (store) and the policy-facing protocol (what callers use); the sidecar and memory stores implement the primitives; chronicle implements the policy over them. The seven direct file users (bridge/status, bridge/resume, session/context, session/cli, session/migrate, episodes/store, recall/index) move onto primitives. Everything else in the earlier decisions stands (session id stable, recall inside the policy's first append, default-session, Discord/ACP follow-ups).



## Planted (updated 2026-09-09 17:35Z) — isaac-agent main f3dd87e
Feature renamed to `features/session/session_policy.feature` (vocabulary: session-policy, 'a recording session policy "logbook" is registered', 'the logbook policy recorded calls matching:'). Acceptance selectors:
```
cd isaac-agent
bb features features/session/session_policy.feature:23
bb features features/session/session_policy.feature:51
bb features features/session/session_policy.feature:71
bb features features/session/session_policy.feature:83
bb features features/session/session_policy.feature:120
bb features features/session/session_policy.feature:144
bb features features/session/session_policy.feature:172
bb features features/session/session_policy.feature:207
bb features features/bridge/suspend.feature && bb features && bb spec
```
The earlier acceptance block's grep lines stand, with `:session-store` read as `:session-policy`. Train note: zanebot `crew/marvin.edn` `:conversation :episodes` → `:session-policy :episodes`.

## CI repair (2026-09-09, scrapper@isaac-work-2)

Main CI Tests (runs 34383400840 @ f3dd87e, 34384940799 @ 1f72fa3) failed at **Install ripgrep** — `apt-get update` exit 100, Hash Sum mismatch on `dl.google.com/linux/chrome-stable`. Not a product failure; `bb ci` never ran.

Repair on `bean/isaac-mmod` and fast-forwarded to `origin/main`: **de3af25** — install ripgrep from the GitHub musl release (same pattern as the isaac monolith workflow). Trailers Isaac-Session: isaac-work-2 / Isaac-Bean: isaac-mmod.

Session-policy implementation remains local WIP; this commit is CI-only.


## Progress (2026-09-10, scrapper@isaac-work-2, continuation 3)

WIP checkpointed and pushed: isaac-agent `bean/isaac-mmod` @ **8a48086** (base was origin/main@f7432c2 / 0.1.53). 38 files: SessionPolicy protocol + chronicle/episodes factories, conversation.router deleted, logbook fixture, crew `:session-policy` check, callers wrap via `policy/for-request`.

**Done:** protocol + berth, chronicle/episodes policies, logbook recorder, config check, feature recut `session_policy.feature` (8 scenarios, no @wip), feature recuts conversation→session-policy.

**Exact next step:** rebase `bean/isaac-mmod` onto current `origin/main` (0.1.55 @ 6bbae8d), then run `bb features features/session/session_policy.feature` and close remaining gaps (default-session CLI, unknown-policy known-names, episodes cold-open session-id).

## Progress (2026-09-10, scrapper@isaac-work-2)

isaac-agent `bean/isaac-mmod` @ **f50c77b** (base origin/main@a89cf6e / 0.1.57).

**Done:** SessionPolicy berth (chronicle/episodes), recall on first user append, live.feature recut to policy+lifecycle (stable session-id; TTL via worker tick), isaac-jom5: compact-chain! returns `:successor-container` so perform-compaction! logs `:session/compaction-completed` instead of `:no-progress` when the live estimate stays put. `features/episodes/live.feature` 19/0, `session_policy.feature` 8/0, `idle_seal.feature` green, `suspend.feature` 3/0.

**Handoff:** branch: bean/isaac-mmod @ f50c77b (base origin/main@a89cf6e).



## Verify fail (attempt 1, 2026-09-10): prompt --crew bypasses frequencies (:create/:prefer); cli-prompt.feature 3 red

HEAD: f50c77b47210d91209604ac384b7b7ef80f47c02 (isaac-agent bean/isaac-mmod; merge-base origin/main a89cf6e)
Working tree: clean

### Blocking (isaac-agent)

`src/isaac/bridge/prompt_cli.clj` `resolve-target` short-circuits any `--crew` without `--session`/`--resume`/tags to `policy/default-session` (chronicle = most-recent session for that crew). That skips `session-frequencies/resolve-session-targets`, so `:create` and `:prefer` are ignored.

Reproduce:
```
cd isaac-agent   # at f50c77b
rm -rf target/gherclj/generated/
bb features features/bridge/cli-prompt.feature
```
30 examples, **3 failures**, 58 assertions:
1. `--create never with no match errors` (cli-prompt.feature:199) — expected stderr "no session"; got none (default-session created/opened a session instead of erroring).
2. `--create always starts a fresh session and leaves the matching one untouched` (:220) — expected session count 2, got 1 (appended to existing ketch-session).
3. `--prefer oldest picks the oldest of multiple matching sessions` (:277) — wrote onto `recent` instead of `older`.

Same 3 failures on JVM `clojure -M:features -t '~slow' -t '~wip'`: **797 examples, 3 failures, 2143 assertions** (71.4s). Native `bb features` (full suite) hit the 180s timeout after printing those Fs.

### Acceptance greps (partial)

- `features/session/session_policy.feature`: 8/0, `@wip` removed. Feature diffs vs origin/main are `@wip` removal only.
- `features/bridge/suspend.feature`: 3/0.
- `features/episodes/live.feature`: 19/0; idle_seal+recall_logging 12/0; index+migrate+recall 18/0.
- `bb spec`: **1726 examples, 0 failures, 3630 assertions** (9.06s).
- Caller-dir grep `episodes-crew?|resolve-thread!|maybe-recall-at-open!|maybe-seal!|compact-close!|successor-session-key` in `src/isaac/bridge` `drive` `session` `comm`: empty except `session/policy/episodes.clj` calling `lifecycle/maybe-seal!` (policy-internal, allowed).
- `grep :conversation src spec features` is **not** empty: leftover `spec/isaac/episodes/lifecycle_spec.clj:649` still `{:conversation :episodes}` (isaac-9tjo tick example; two sibling its were recut to `:session-policy`). Production `src/` is clean.

### Downstream (bean said must stay green vs this SHA)

- isaac-acp `features/comm/acp/episodes.feature` against local agent: 4/0.
- isaac-discord `features/comm/discord/episodes.feature` against local agent: **3 examples, 2 failures** (no episode opened; crew has 0 episodes). Feature still plants `:conversation | episodes`. Bean text both (a) carves Discord/ACP as follow-up beans and (b) requires those suites green against this SHA. Discord is red.

### Not a fail on its own

session_policy 8 scenarios + berth `:isaac.agent/session-policy` + factories exist. Do not land until cli-prompt frequencies work again and `bb features && bb spec` is green.


## Repair (attempt 1, 2026-09-10, scrapper@isaac-work-1)

isaac-agent `bean/isaac-mmod` @ **1fa612e** (base origin/main@a89cf6e / 0.1.57).

**Fix:** `prompt_cli/resolve-target` uses `policy/default-session` only for `--crew` with no session id **and** no frequencies (`--create`/`--prefer`/`--session`/`--resume`/tags). `--create never` errors without opening; `--create always` starts a fresh session; `--prefer oldest` picks the oldest matching crew session. Bare `prompt -m` stays on `prompt-default`. `ensure-session!` create uses `(:session-key target)` only (nil → memory `"session"`). Leftover `lifecycle_spec` plant recut `:conversation` → `:session-policy`.

**Green:**
- `bb features features/bridge/cli-prompt.feature` 30/0/61
- `bb features features/session/session_policy.feature` 8/0/23 (scenario 8 still asks default-session)
- `bb features features/bridge/suspend.feature` 3/0
- `bb features features/episodes/live.feature` 19/0
- `bb spec` 1729/0/3637
- `grep :conversation src spec features` empty
- caller-dir `episodes-crew?|resolve-thread!|maybe-recall-at-open!|maybe-seal!|compact-close!|successor-session-key` empty except `session/policy/episodes.clj` calling `lifecycle/maybe-seal!` (policy-internal)

**Handoff:** branch: bean/isaac-mmod @ 1fa612e (base origin/main@a89cf6e). Discord episodes plant re-key is verifier-local per planner note 209c8f46.



## Verify fail (attempt 2, 2026-09-10): Discord/ACP episode surfaces still red vs this SHA after frequencies repair

HEAD: 1fa612ed6c06de2d6b7446df34ba80b0a153569d (isaac-agent bean/isaac-mmod; merge-base origin/main a89cf6e)
Working tree: clean

### Agent gates (green — frequencies repair holds)

- bb spec: 1729 examples, 0 failures, 3637 assertions (9.57s)
- session_policy.feature: 8/0/23, @wip removed (diff vs origin/main is @wip-only)
- cli-prompt.feature + suspend.feature: 33/0/73
- episodes live+idle_seal+recall_logging: 55/0/205
- episodes index+migrate_session + recall/: 18/0/134
- grep :conversation src spec features: empty
- caller-dir episodes-crew?|resolve-thread!|maybe-recall-at-open!|maybe-seal!|compact-close!|successor-session-key empty except session/policy/episodes.clj calling lifecycle/maybe-seal! (policy-internal)
- berth :isaac.agent/session-policy + chronicle/episodes factories exist; session id is stable (prompt_cli_spec --session = reef-chat)

### Blocking (downstream vs this SHA)

Bean acceptance: isaac-discord features/comm/discord/episodes.feature and isaac-acp features/comm/acp/episodes.feature must stay green against this agent SHA. Planner 209c8f46 authorized a verifier-local Discord plant re-key conversation→session-policy.

**Discord** (local plant re-key applied, then reverted after the run): 3 examples, **2 failures**, 5 assertions.

1. first message … opens an episode (episodes.feature:32) — no episode exists
2. warm second message … ( :70) — crew has 0 episodes (expected 1)

Root cause is Discord **production** code, not the plant line. isaac-discord src/isaac/comm/discord.clj process-message! still:

- episode? = lifecycle/episodes-crew? (now correctly reads :session-policy)
- episode? → assoc :conversation {:kind :thread :id session-name} and **does not set :session-key**
- not episode? → :session-key session-name

Agent bridge no longer routes on :conversation (router deleted). Without :session-key, dispatch never opens a session, so the episodes policy never opens a container. Chronicle scenario still green. This is the Discord episode/chronicle request-shape branch the bean carved as a follow-up.

**ACP** against local agent (bb jvm-features / :dev-local): 4 examples, **3 failures**, 14 assertions.

1. session/prompt opens an episode (expected :episodes/opened thread=reef-chat; got :session/behavior-resolved, thread nil)
2. warm second prompt (expected episode-id regex session; got session id "reef-chat")
3. --crew attaches to a fresh thread and replays nothing — failed

Native `bb features` on isaac-acp is 4/0 only because it uses the **pinned** agent SHA (bf43233), not this branch.

ACP production still branches on episodes-crew?: session/new for episodes returns a thread id without creating a session; attach-session replays store/active-transcript of the **episode id**, not the stable session-id.

### Contract conflict for planner

Bean body both (a) carves Discord/ACP episode branches as follow-up beans after train and (b) requires those suites green against this SHA. Attempt 1 bounced on cli-prompt frequencies (fixed @ 1fa612e) plus Discord red. Attempt 2: agent acceptance is green; Discord/ACP remain red for the same module-side reason. Do not land. Do not return to the worker to "fix Discord in isaac-agent" — the agent seam is the recut; the remaining red is isaac-discord / isaac-acp request-shape.

Need planner to either: waive downstream-green until the follow-up beans, or expand this bean to recut Discord/ACP dispatch onto :session-key (and recut their episode assertions: session id never changes, no :conversation on the charge).


## Planner adjustment (2026-09-10, prowl@isaac-plan) — waive downstream Discord/ACP green; agent seam controls

Verify fail attempt 2: agent gates on `bean/isaac-mmod` @ `1fa612e` are green (frequencies repair holds). Remaining red is **module request-shape**, not the agent seam.

**Decision: waive downstream-green until the follow-up beans.** Do **not** expand this bean. Do **not** return to the worker to recut Discord or ACP inside isaac-agent. Do **not** land until the **agent** acceptance below is green (already claimed).

Micah already carved this: Discord's episode/chronicle request-shape branch collapses to a plain session id; ACP session/new and session/load collapse to `default-session` / `open-session!` / `active-transcript` of the **stable session id**. Those land in the module repos after this train.

### Follow-ups (do not implement here)

- **isaac-ru3e** (draft, blocked by this bean) — isaac-discord: `process-message!` always sets `:session-key`; drop the episode? branch that puts `:conversation {:kind :thread}` on the charge and omits `:session-key`. Plant re-key `:conversation` → `:session-policy` lands there, not as a verifier-local patch.
- **isaac-0yoc** (already todo, blocked by this bean) — isaac-acp: session/new and session/load stop branching on episodes-crew?; no `isaac.episodes` requires. Attempt-2 evidence: session/new still mints a thread without creating a session; attach-session replays `active-transcript` of the **episode id**, not the stable session-id. Native `bb features` 4/0 on ACP is the **pinned** agent (`bf43233`), not this branch — ignore that as evidence against mmod.

### Acceptance (supersedes the downstream-green clause)

Verifier runs **only** isaac-agent, on `1fa612e` (or its rebased equivalent):

    cd isaac-agent
    bb features features/session/session_policy.feature
    bb features features/bridge/cli-prompt.feature features/bridge/suspend.feature
    bb features features/episodes/live.feature features/episodes/idle_seal.feature features/episodes/recall_logging.feature
    bb features features/episodes/index.feature features/episodes/migrate_session.feature features/recall
    bb spec

0 failures on each. `@wip` removed from session_policy.feature. `grep -rn ':conversation' src spec features` empty. Caller-dir grep `episodes-crew?|resolve-thread!|maybe-recall-at-open!|maybe-seal!|compact-close!|successor-session-key` empty except `session/policy/episodes.clj` calling `lifecycle/maybe-seal!` (policy-internal).

Do **not** require:
- isaac-discord `features/comm/discord/episodes.feature` green against this SHA
- isaac-acp `features/comm/acp/episodes.feature` green against this SHA (dev-local or pinned)
- a verifier-local Discord plant re-key
- recutting Discord/ACP production code in isaac-agent
- full `bb features && bb spec` as a controlling gate (the named commands above are the gate)

Train note still stands: zanebot `crew/marvin.edn` `:conversation :episodes` → `:session-policy :episodes` at deploy. Discord/ACP recuts (ru3e, 0yoc) pin the agent SHA this bean releases.
