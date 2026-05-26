---
# isaac-bgx0
title: Model-driven skill auto-activation (advertise descriptions + load-on-demand tool)
status: draft
type: feature
priority: normal
created_at: 2026-05-26T04:21:33Z
updated_at: 2026-05-26T04:21:33Z
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
