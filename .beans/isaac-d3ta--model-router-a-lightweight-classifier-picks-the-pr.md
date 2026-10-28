---
# isaac-d3ta
title: 'Model router: a lightweight classifier picks the provider and model for each turn'
status: draft
type: feature
priority: normal
tags:
    - agent
created_at: 2026-10-28T08:07:33Z
updated_at: 2026-10-28T08:07:33Z
---

Repo: **isaac-agent** (drive, model resolution). Draft from Micah's idea, 2026-10-28. Design to settle before scenarios.

## Idea

Today every crew names its model, and every turn on that crew uses it, whether the prompt is "pong" or "redesign the session store". Instead, a crew can leave model choice to a small, fast classifier. The classifier reads the incoming prompt and picks the provider and model that fit it. Cheap prompts go to cheap, fast models. Hard ones go to the strong models. The crew keeps its soul, tools and directories; only the model becomes a decision.

## Sketch (not decided)

- **Tiers, not raw models.** The classifier picks a tier from a short, fixed list (say `:quick`, `:standard`, `:deep`). Config maps each tier to a model, so the classifier never invents a model name, and changing a model is a config edit.
  `:defaults :router {:model :<classifier-model> :tiers {:quick … :standard … :deep …}}`; a crew opts in with `:model :auto` (or a `:router` key). An explicit `:model` keeps today's behavior.
- **What the classifier sees:** the new prompt, the crew's name and a one-line role, and maybe the tool allowlist. Not the transcript; it has to be fast and cheap.
- **What it returns:** one tier and a short reason, in a constrained format (enum or JSON).
- **When:** once, at turn start. The whole turn stays on that model.
- **Failure:** a classifier error or timeout (target well under 1s) falls back to the crew's default tier. The turn never waits on the router.
- **Visibility:** log `:router/decided` with tier, model, ms and reason, and show the chosen model on the turn record.
- **Shadow mode first:** classify and log, but don't act, so we can judge its choices on real traffic before it routes anything.

## Open questions

1. **Session stickiness.** Switching models mid-session loses the prompt cache and can confuse continuity. Route every turn, only the first turn of a session (or after a reset), or allow up-shifts only?
2. **Which classifier model.** A local small model on zanebot (no quota, no network) or the cheapest API model? A keyword or length heuristic in front for the obvious cases?
3. **Does it choose the provider too?** Provider quota reports (isaac-7q4c, just landed) let the router steer away from a provider near its limit. That overlaps the fallback chain (isaac-xd2z); decide which owns the choice.
4. **Per-crew tiers.** Can a crew override the tier map (e.g. a coding crew whose `:quick` is still a coding model)?
5. **Hail and Foreman turns.** Do work and verify turns route, or stay pinned? Bean work probably stays pinned at first.

## Related

- isaac-xd2z model fallback chain (moves a turn when a provider is exhausted; routing happens before it).
- isaac-7q4c provider quota reports.
- isaac-p5d3 subagents (a subagent's model could be routed the same way).

## Acceptance

TBD with scenarios. Candidates (grover models): an `:auto` crew's quick prompt runs on the `:quick` model and its log carries the decision; a classifier timeout runs on the default tier; an explicit `:model` crew never calls the classifier; shadow mode logs a decision but runs on the crew's model.
