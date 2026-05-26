---
# isaac-bgx0
title: Model-driven skill auto-activation (advertise descriptions + load-on-demand tool)
status: completed
type: feature
priority: normal
created_at: 2026-05-26T04:21:33Z
updated_at: 2026-05-26T23:16:48Z
parent: isaac-nwj3
blocked_by:
    - isaac-8qd5
---

Deferred from isaac-nwj3. **Model-driven** skill activation: the model picks a skill by matching its `description` to the task (vs the MVP, where commands pre-include skills by name).

## Mechanism
- **Advertise** skill name+description (from the session frontmatter index) into the **cached system prompt** (stable per project) — or, for large/churny sets, via a `list_skills` tool instead of an always-on menu.
- The model calls **`load_skill <name>`** to pull the body on demand into the turn.

## Cache
Menu-injection means a skill-set change busts the prompt cache (rare, dev-time). **Deterministic menu rendering required** (stable sort + format) so unchanged sets don't bust per turn. Knob: inject-menu vs tool-only.

Parent: isaac-nwj3. Builds on the discovery/registry (the frontmatter index already holds descriptions).


## Decided design (progressive disclosure, matches Anthropic Agent Skills)

Not either/or — two tiers by role:

- **Descriptions -> cached system prompt.** The skill menu (name + "use when ...") goes in the system prompt so the model can match a task to a skill *before* acting. Cheap (short), stable per project -> caches within a session (only a rare dev-time skill edit busts it). **Deterministic rendering required** (stable sort + format) so an unchanged set does not bust the cache per turn.
- **Bodies -> `load_skill` tool.** Once the model picks a skill it calls `load_skill <name>`; the full body loads into the turn. Only activated skills are loaded.

This is the industry pattern (Anthropic Agent Skills progressive disclosure: name+description always in context, body on trigger, bundled files as needed).

**Large-set knob:** if a project has many skills, the descriptions bloat the cached system prompt -> fallback to a `list_skills` tool (descriptions on demand, nothing in the prompt). Threshold-based, per deployment.

**Relationship to MVP:** the MVP (isaac-dbg1) does NOT touch this — MVP skills are command-included, inlined into the user turn deterministically, nothing in the system prompt, no tool. This bean adds the ad-hoc, model-driven path.


## load_skill shape: resource-arg-ready

Shape the tool as `load_skill(name, [resource])`. MVP of this bean implements `load_skill(name)` -> body only, but the signature anticipates an optional `resource` arg so bundled-resource loading (isaac-etpt) slots in without a redesign. `load_skill` is a **scoped reader** of a skill's own directory (it can read skill assets even though they live under config/, outside general crew fs-bounds).


## Feature file

`features/prompts/skill_activation.feature` — 3 `@wip` scenarios: skills advertised (name+description) in the cached system prompt; `load_skill` loads a body on demand; menu rendered in stable sorted order (cache-safe). Run:

```
bb features features/prompts/skill_activation.feature
```

**Definition of done:** remove `@wip` and green.

**Acceptance (not scenarios):** debug-safe deterministic menu rendering; large-set `list_skills` fallback (threshold knob).

**New step:** none (reuses `the prompt ... matches:`, queued tool_call responses, `the tool result lines match:`).
