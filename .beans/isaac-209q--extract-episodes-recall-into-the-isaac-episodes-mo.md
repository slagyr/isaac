---
# isaac-209q
title: Extract episodes + recall into the isaac-episodes module behind the session-store berth
status: in-progress
type: task
priority: high
created_at: 2026-09-09T16:35:02Z
updated_at: 2026-09-11T06:20:02Z
blocked_by:
    - isaac-mmod
---

Repo: new isaac-episodes (Micah names it) + isaac-agent + isaac registry. Blocked by isaac-mmod. Moves `src/isaac/episodes/*`, `src/isaac/recall/*` (~3,100 lines), `features/episodes/*`, `features/recall/*`, `spec/isaac/episodes/episode_steps.clj` (20 steps) and the CLI commands (episodes, recall, embed), the recall tools, the embedding-provider config check, and the idle-seal scheduler task into the module; the module's manifest contributes `:isaac.agent/session-store {:episodes …}`, `:isaac.agent/tools`, `:isaac/cli`, `:isaac.config/check`. Agent keeps chronicle as the default store and no episode knowledge. Like isaac-jllj: an extraction, not a rewrite — moved scenarios stay byte-identical except the ns moves; acceptance = module `bb features && bb spec` green, agent `bb features && bb spec` green with `grep -rn 'isaac\.episodes\|isaac\.recall' src spec features` empty, session_store.feature scenarios 6–7 (episodes cold open / warm append) move to the module's feature file with the agent keeping 1–5 and 8. Train: new registry entry (`isaac modules install`) + agent bump. Arcs come after this.


## Held (awaiting human, 2026-09-11)

Escalated to human by **scrapper**@isaac-work-1. Blocking: the required `slagyr/isaac-episodes` repository does not exist, and the configured GitHub identity `slagyr-assistant` is not authorized to create repositories for `slagyr` (`gh repo create` GraphQL permission denied).
Resumes only on explicit human action (create `slagyr/isaac-episodes`, grant write access, then re-hail the work band). No crew re-picks this until then.

## Planner (2026-09-11 06:0xZ): repo created

slagyr/isaac-episodes created (public, like its siblings) by Micah's account; slagyr-assistant invited with write (invitation 332621113 — the assistant's gh keyring is unreadable over ssh, so the worker accepts it at the top of the re-hailed turn). Re-hailed on isaac-work with a prompt override that prepends the acceptance step to the band template.
