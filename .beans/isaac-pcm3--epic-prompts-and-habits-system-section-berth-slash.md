---
# isaac-pcm3
title: 'Epic: prompts and habits — system-section berth, slash providers, prompt module, dreaming'
status: draft
type: epic
priority: normal
created_at: 2026-10-08T20:38:14Z
updated_at: 2026-10-08T20:38:14Z
---

DESIGN, from a planning conversation with Micah on 2026-10-08. Nothing here is dispatchable yet; each child is a draft awaiting scenarios.

## Motivation

Skills, rules, slash commands and `AGENTS.md` are all "prompts": predefined text Isaac puts in front of the model. No provider API knows about any of them. The APIs take a system prompt, messages and tools; rules and the skill menu are text Isaac concatenates into the system prompt, and a slash command is expanded into the user message before the request is built.

Today that behavior is wired into `isaac-agent` with no extension point:

- `isaac.agent.llm.prompt.builder/build-system-text` takes four fixed arguments: soul, boot files, rules text, skill menu text.
- `isaac.agent.drive.turn` calls the prompt catalog directly to fill them.
- `isaac.agent.bridge.core/handle-slash` falls back to the prompt catalog when no registered slash command matches.

Two things want the extension point: pulling prompts out into their own module, and a new dreaming module whose "habits" are private to one crew.

## Children

1. System-section berth: modules contribute ordered sections of the system prompt.
2. Slash-command berth becomes providers: a provider can answer a name at runtime and can start a turn, not only reply.
3. Decision: extract prompts into a module. Carries the line counts and the estimate.
4. Dreaming module: a crew distills its conversations into habits that only it sees.

Order: 1 and 2 are independent. 3 needs both. 4 needs only 1.

## Rulings so far (Micah, 2026-10-08)

- The umbrella word stays "prompts". "Context" is the industry's nearest term but already means the transcript window in Isaac.
- Dream products are NOT skills and do not follow the skill file structure. They are kept separate from the prompt catalog, in a structure convenient to dreaming.
- They are called **habits**. ("Behaviors" was considered; `resolve-behavior` in `isaac.agent.session.context` already uses that word for a session's resolved settings.)
- Skills keep the open `SKILL.md` layout on disk, so skills written for other tools still drop in. Skills are not forced into the config-entity shape.

## Open questions

- Crew-owned prompts written by a person (not dreamed): a third catalog layer at `<root>/crew/<id>/prompts/`, beside `SOUL.md`. Proposed precedence global, then crew, then project. Not yet a bean; decide after child 3.
- Whether the global prompt layer should become a config table (schema validation, `isaac config get/set`, hot reload). Hail bands already use the frontmatter-plus-body shape. Not decided.
