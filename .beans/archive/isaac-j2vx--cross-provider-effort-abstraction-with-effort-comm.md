---
# isaac-j2vx
title: "Cross-provider effort abstraction with /effort command and session knob"
status: completed
type: feature
priority: low
created_at: 2026-05-01T18:24:06Z
updated_at: 2026-05-11T23:54:55Z
---

## Description

Isaac exposes a single integer effort knob (0-10) that applies to
every model and provider. The universal layer resolves the value
from the configuration chain and attaches :effort to the request
map before any API adapter touches the wire shape. Per-provider
adapters translate :effort to whatever their API expects.

## Resolution chain (top wins)

  session > crew > model > provider > defaults.effort > 7

## Capability gate

Each model declares allows-effort (default true). When false, the
universal layer omits :effort from the request entirely; API impls
never see it for that model.

## Naming choices

- Universal Isaac config key: :effort (replaces :reasoning-effort
  at every tier). Hard rename, no aliasing.
- Wire-shape translation lives in each API impl (openai-completions,
  openai-responses, anthropic-messages, ollama, etc.). Each owns its
  effort -> wire-params function.

## /effort slash command

  /effort N     — set session-level effort (0-10)
  /effort       — show the current effective effort
  /effort clear — remove the session-level override

Out-of-range and non-numeric inputs are rejected with a clear error;
session is unchanged.

## Acceptance Criteria

- @wip removed from all four new feature files.
- bb features green for all four feature files.
- features/llm/reasoning_effort.feature deleted.
- :reasoning-effort no longer referenced anywhere in src/ or spec/.
- reasoning-model? predicate removed from
  src/isaac/llm/api/openai/shared.clj.
- New step "the last LLM request matches:" registered and used by
  features/llm/effort.feature.
- ISAAC.md has an "Effort" section defining the universal knob.
- isaac-qtui can land independently once this is in.

## Design

## Feature files (all @wip)

- features/llm/effort.feature
  Universal semantics. Resolution chain, defaults.effort,
  allows-effort, effort 0. Assertions against the pre-API request
  via new step "the last LLM request matches:" (reads
  grover/last-request). API-agnostic.

- features/bridge/effort.feature
  /effort command behaviors. Mirrors features/bridge/model.feature.

- features/llm/api/openai_completions.feature
  Wire translation Outline: integer effort -> top-level
  reasoning_effort.

- features/llm/api/openai_responses.feature
  Wire translation Outline: integer effort -> nested
  reasoning.effort.

## Implementation outline

1. Schema rename: :reasoning-effort -> :effort at provider, model,
   crew, and session tiers (and a new top-level defaults.effort).
   Values are integers 0-10 (was strings low/medium/high).

2. Add :allows-effort to model schema; default true.

3. Drop name-based reasoning-model? predicate in
   src/isaac/llm/api/openai/shared.clj. Replace with allows-effort
   lookup.

4. Add a universal-effort resolver (probably a function in
   src/isaac/drive/turn.clj or a new ns under src/isaac/effort.clj)
   that walks the chain, applies the allows-effort gate, and
   attaches :effort to the request map before dispatch.

5. Each API impl reads :effort from the request map and translates
   to its wire shape (omit field on 0; map to enum or budget).

6. New gherclj step "the last LLM request matches:" — asserts
   against (grover/last-request) using the existing match DSL.

7. /effort slash command in src/isaac/slash/builtin.clj. Mirrors the
   /model and /crew handlers.

8. Define "effort" in ISAAC.md as Isaac's universal cross-provider
   knob.

9. Delete features/llm/reasoning_effort.feature as the final step.

## Per-provider adapters

- openai-completions, openai-responses: covered by this bead's API
  feature files.
- anthropic-messages: sister bead isaac-qtui (no longer blocking).
- grok-3-mini (low/high subset): future bead.
- gemini-2.5 (thinkingBudget int): future bead.
- ollama (think bool): future bead.

## Notes

Verification failed: bb spec passed, but the bead's required feature verification failed because features/llm/effort.feature does not exist. Related acceptance criteria also appear unmet: features/llm/reasoning_effort.feature still exists, and :reasoning-effort is still referenced under src/ and spec/.

