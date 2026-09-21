---
# isaac-oys6
title: An always-on, install-wide Isaac system prompt
status: draft
type: feature
priority: normal
created_at: 2026-09-21T16:22:55Z
updated_at: 2026-09-21T16:23:24Z
blocked_by:
    - isaac-jl9p
---

## Why (Micah, 2026-09-21)

OpenCode opens every prompt by saying what the harness is and how the model
should treat the user. Isaac has nothing like that: the system prompt is the
crew soul, AGENTS.md, rules, the skill menu and some fixed blocks. An install
may also want behavior it imposes **no matter which crew or model is
running**.

The per-model `:extra-system-prompt` (isaac-5n68) can't do that. It cascades
with override semantics, so a crew's or model's own text *replaces* the
default. This prompt is separate and additive.

## Design (to be settled)

- A root config key, working name `:system-prompt`, holding text (normally
  `"${file:prompts/isaac.md}"`, isaac-jl9p). Not in `:defaults`: it isn't a
  default anything overrides.
- **Always included, never overridden** by crew, model or session text.
- **Placed first** in the system prompt, before the soul. It establishes the
  platform; the soul then establishes the character.
- A built-in value ships and is scaffolded to disk (the scaffolding bean), so
  it is visible and editable. What that built-in text says is its own
  decision.
- It sits in the cached prefix; it's static per install.

## Overlap to resolve first

Global rules (`~/.isaac/prompts/rules/*.md`) are already always included, in
every session, for every crew. Before building this, decide what this prompt
is for that rules aren't. Candidates: it is one text rather than a collection
of snippets, it comes first rather than after AGENTS.md, and it ships with a
built-in value. If the answer is "nothing," this becomes a scaffolded rule
instead.
