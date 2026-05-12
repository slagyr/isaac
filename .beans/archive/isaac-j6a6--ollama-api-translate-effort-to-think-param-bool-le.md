---
# isaac-j6a6
title: "Ollama API: translate :effort to think param (bool/levels)"
status: completed
type: feature
priority: low
created_at: 2026-05-11T22:12:17Z
updated_at: 2026-05-12T00:28:20Z
---

## Description

Ollama's thinking knob is body.think — a boolean for most
thinking-capable models, or a "low"|"medium"|"high" string on newer
models that accept tier vocabulary.

The ollama API impl reads :effort from the request map (0-10) and
translates per the model's think-mode:

  :bool (default) — effort 0 -> think:false; effort 1-10 -> think:true.
    Universal across thinking-capable Ollama models. Loses tier
    granularity.

  :levels (opt-in) — effort 0 -> omit; 1-3 -> "low"; 4-6 -> "medium";
    7-10 -> "high". For Ollama models that accept tier strings.

Models that do not support any thinking knob set
allows-effort: false at the model tier; the universal layer omits
:effort from the request entirely for those models, so this adapter
never sees it.

Spec: features/llm/api/ollama.feature (two outlines, @wip)
Run:  bb gherclj features/llm/api/ollama.feature

## Acceptance Criteria

- @wip removed from features/llm/api/ollama.feature.
- Both outlines pass under bb features.
- Model config schema adds :think-mode with the description above.
- ollama API reads :effort from request map (not from config
  directly) and produces body.think per the bool/levels rules.
- No regressions in existing ollama feature/spec files.

## Design

## Schema change

Add to model schema in src/isaac/config/schema.clj:

  :think-mode
    {:type :keyword
     :description "Ollama-specific: how the API impl translates
                   :effort. :bool (default) collapses to think
                   true/false; :levels buckets to \"low\"|\"medium\"
                   |\"high\" for models that accept tier strings."}

## Adapter change

In src/isaac/llm/api/ollama.clj, add an effort->think function:

  (defn- effort->think [effort think-mode]
    (case (or think-mode :bool)
      :bool   (if (and effort (pos? effort)) true false)
      :levels (cond
                (or (nil? effort) (zero? effort)) nil  ;; omit
                (<= 1 effort 3) "low"
                (<= 4 effort 6) "medium"
                (<= 7 effort 10) "high")))

Merged into the request body as :think. Nil result means omit
the field from the body entirely.

## Depends on

- isaac-j2vx (universal abstraction). j2vx must land first so that
  :effort is on the request map and allows-effort gates
  non-thinking Ollama models.

## Notes

- :bool is the safe universal default. Models that support tier
  strings opt in via :think-mode :levels.

- Future enhancement (separate bead, not blocking): some Ollama
  models also accept :reasoning_effort (OpenAI-style enum). If
  needed, add an :openai-effort mode that mirrors openai-completions
  translation.

