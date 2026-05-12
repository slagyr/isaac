---
# isaac-qtui
title: "Anthropic Messages API: translate :effort to thinking budget"
status: completed
type: feature
priority: low
created_at: 2026-05-01T18:23:53Z
updated_at: 2026-05-12T00:20:51Z
---

## Description

Anthropic's extended-thinking knob is body.thinking.budget_tokens
(integer), not an enum. Different shape from OpenAI's reasoning_effort
and from Ollama's think field.

The anthropic-messages API impl reads :effort from the request map
(0-10) and translates linearly to a budget:

  budget = effort * (thinking-budget-max / 10)

  effort=0    -> omit thinking block entirely
  effort=10   -> 100% of thinking-budget-max (default 32000)

The model config declares thinking-budget-max so the curve scales
per-model. Models that do not support extended thinking
(Claude 3.5/3.7, etc.) set allows-effort: false at the model tier;
the universal layer omits :effort from the request entirely for
those models, so this adapter never sees it.

Spec: features/llm/api/anthropic_messages.feature (two outlines,
  @wip)
Run:  bb gherclj features/llm/api/anthropic_messages.feature

## Acceptance Criteria

- @wip removed from features/llm/api/anthropic_messages.feature.
- Both outlines pass under bb features.
- Model config schema adds :thinking-budget-max with the description
  above.
- anthropic-messages API reads :effort from request map (not from
  config directly) and produces body.thinking.budget_tokens per the
  linear formula.
- No regressions in existing anthropic feature/spec files.

## Design

## Schema change

Add to model schema in src/isaac/config/schema.clj:

  :thinking-budget-max
    {:type :int
     :description "Anthropic-specific: max thinking budget in tokens
                   at effort=10. Budget at other levels scales
                   linearly (budget = effort * max / 10). Read only
                   by the anthropic-messages API. Default 32000."}

## Adapter change

In src/isaac/llm/api/anthropic_messages.clj, add an effort->params
function:

  (defn- effort->thinking [effort budget-max]
    (when (and effort (pos? effort))
      {:type "enabled"
       :budget_tokens (int (* effort (/ (or budget-max 32000) 10)))}))

Called when building the chat request. Reads :effort from the
incoming request map and :thinking-budget-max from the model
config; merges result into the request body as :thinking.

## Depends on

- isaac-j2vx (universal abstraction). j2vx must land first so that
  :effort is on the request map and allows-effort gates non-thinking
  Claude models.

## Notes

- Original bead (2026-05-01) proposed a coarse enum mapping
  (:none/:low/:medium/:high). Superseded by the universal 0-10
  integer design in isaac-j2vx.

- Why linear, not Claude-Code's tiered (low/medium/high/xhigh/max)
  curve: continuous control gives users finer granularity; tiered
  vocabulary is a UX layer that can be added on top later if
  desired.

- Default thinking-budget-max of 32000 reflects Sonnet 4-class
  ceilings; raise to 64000+ in model config for Opus or other
  larger-budget models.

