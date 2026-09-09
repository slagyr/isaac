---
# isaac-mmod
title: 'Session-store berth: chronicle and episodes are session-store implementations selected per crew; the bridge stops resolving episodes'
status: draft
type: feature
priority: high
created_at: 2026-09-09T14:52:09Z
updated_at: 2026-09-09T16:02:31Z
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
