---
# isaac-mmod
title: 'Conversation-mode berth: episodes become a module-provided conversation mode; chronicle stays the agent''s default'
status: draft
type: feature
priority: high
created_at: 2026-09-09T14:52:09Z
updated_at: 2026-09-09T14:52:09Z
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
