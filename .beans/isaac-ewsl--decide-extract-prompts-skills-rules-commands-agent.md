---
# isaac-ewsl
title: 'Decide: extract prompts (skills, rules, commands, AGENTS.md) into an isaac-prompts module'
status: draft
type: task
priority: normal
created_at: 2026-10-08T20:38:14Z
updated_at: 2026-10-08T20:38:14Z
parent: isaac-pcm3
blocked_by:
    - isaac-3dnw
    - isaac-quji
---

DECISION, not yet work. Part of the prompts-and-habits epic. Blocked by the system-section berth and the slash-provider reshape; without them there is nowhere for a module to plug in.

## The question

Should skills, rules, commands and `AGENTS.md` handling move out of `isaac-agent` into a module of their own (working name `isaac-prompts`)?

## Size, measured on isaac-agent a809845 (2026-10-07)

What would move:

| What | Files | Lines |
|---|---|---|
| Source: `prompt/catalog.clj` 378, `tool/prompt.clj` 101, `prompt/template.clj` 8 | 3 | 487 |
| Specs: catalog spec 388, tool spec 145, catalog steps 66, template spec 44 | 4 | 643 |
| Features: `features/prompts/*.feature` | 6 | 541 |
| Total | 13 | 1,671 |

For scale, isaac-agent is 20,381 source lines, 27,815 spec lines and 16,364 feature lines. The moving source is 2.4% of the agent.

What stays behind and must be rewired: about 87 lines across 13 files that name the catalog or pass its four outputs around. `drive/turn.clj` holds 36 of them, `llm/prompt/builder.clj` 13, `session/context.clj` 10, `drive/accounting.clj` 7. Most of that rewiring is done by the two berth beans, not by the extraction.

Config keys that would move to the module's schema: `:prompt-dir-names`, `:prompt-paths`, `:command-paths`, `:skill-paths`, `:skill-menu-threshold`.

## What it means for the agent

- The agent loses about 490 source lines and gains the berth code, perhaps 100 to 150 lines. Net it shrinks by a few hundred lines. This is not a size win.
- The win is the boundary: the agent stops knowing what a skill, rule or command is. It knows a soul, ordered sections, slash providers and tools.
- Three agent features that are not about prompts exercise skills along the way (`tool/permissions`, `tool/window_cache`, `session/request_accounting`, 504 lines together). They would need a small fixture contributor in place of real skills.

## Costs

- A new repo with its own pin, CI, handbook chapter and a place on the deploy train.
- Every crew relies on skills and `AGENTS.md`. The module has to be installed on every host, so in practice it is not optional.
- `read-boot-files` and project-root discovery live in the catalog. Either `AGENTS.md` moves with the module or project-root discovery is split out for the agent to keep.

## Recommendation (planner)

Do the two berth beans regardless; dreaming needs the first and the bridge is cleaner for the second. Decide extraction afterwards, when the in-agent contributors show how thin the seam really is. The move itself is then mostly a file move.

## Likely repo scope

`isaac-agent`, plus a new `isaac-prompts` repo if the answer is yes.
