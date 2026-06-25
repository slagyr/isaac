---
# isaac-3eqp
title: 'Anthropic adaptive thinking: migrate Sonnet/Opus 4.6 to thinking.adaptive + effort (fixes max_tokens vs budget_tokens)'
status: todo
type: bug
priority: high
created_at: 2026-06-25T15:28:39Z
updated_at: 2026-06-25T16:19:49Z
---

Anthropic Messages requests can send a thinking `budget_tokens` larger than `max_tokens`, which the API rejects: `max_tokens must be greater than thinking.budget_tokens`. Any fresh claude session that doesn't override effort hits this and CANNOT take a turn.

## Mechanism (isaac-agent, src/isaac/llm/api/messages.clj)
- `request-body` hardcodes `:max_tokens` default **4096** (`:or {max-tokens 4096}`); nothing in the config chain sets it (model-cfg only feeds :thinking-budget-max/:enforce-context-window/:think-mode — never max-tokens).
- `effort->thinking`: `budget_tokens = (int (* effort (/ (or budget-max 32000) 10)))`.
- `default-effort` is **7** (isaac.effort, since 2026-06-14). Resolution chain: session > crew > model > provider > defaults > 7.
- So a turn with no explicit effort -> effort 7, budget-max 32000 -> budget_tokens = 22400 > max_tokens 4096 -> API 400.

## Why it's latent / surfaced now
Existing sessions had `:effort 0` set (no thinking) so they worked; a FRESH claude session falls through to default 7 and breaks. `isaac prompt` (CLI) doesn't resolve effort -> no thinking block -> appears fine. So it specifically hits new claude sessions (Discord/iMessage/remote). Pre-existing; not introduced by a recent deploy (messages.clj/effort.clj unchanged across recent agent shas).

## Core problem
max_tokens (the Anthropic field) is the TOTAL cap and must exceed budget_tokens; response tokens = max_tokens - budget_tokens. A fixed 4096 can't coexist with any meaningful thinking budget (even effort 1 -> budget 3200 leaves ~900 response tokens). max_tokens must scale with the thinking budget.

## Fix (design approved 2026-06-25, Micah) — ADAPTIVE THINKING (option A)

Research (Anthropic docs, 2026-06-25) changed the fix: `thinking.type:"enabled"` + `budget_tokens` is **DEPRECATED** on Sonnet 4.6 / Opus 4.6 (and rejected on Opus 4.7/4.8) in favor of **adaptive thinking** — `thinking:{type:"adaptive"}` + a native categorical **effort** (`output_config.effort`: low/medium/high/xhigh/max). Adaptive thinking has NO budget_tokens, so the `max_tokens > budget_tokens` constraint disappears; `max_tokens` is just a hard cap on total output. zanebot runs claude-sonnet-4-6, so this is THE path. (See Sources in Notes.)

Branch the messages adapter on the existing per-model `:think-mode` field (same mechanism ollama already uses via `(case (or think-mode ...) )`). Resolve.clj already selects `:think-mode` into the request cfg.

### `:think-mode :adaptive` (default for Anthropic 4.6+; the live path)
- Body: `:thinking {:type "adaptive"}` plus `:output_config {:effort <level>}`.
- Map isaac's 0-10 effort -> Anthropic level using only the universally-available levels (skip `xhigh`, which is model-specific — Opus 4.7/4.8/Fable/Mythos only, NOT Sonnet 4.6):
  - 0 -> omit thinking (`{:type "disabled"}` or omit) — no extended thinking.
  - 1-3 -> `low`
  - 4-6 -> `medium`
  - 7-9 -> `high`   (Anthropic's own default)
  - 10  -> `max`
- `max_tokens` = a plain hard cap (NO budget arithmetic). Default **16000** (Anthropic's recurring example value); per-model overridable. (xhigh as an opt-in for capable models can be a later enhancement.)

### `:think-mode :off`
- No thinking block ever (regardless of effort); `max_tokens` = the flat cap default.
- **Use for non-adaptive Anthropic models.** Adaptive is supported only on Sonnet 4.6 / Opus 4.6+ (and Fable/Mythos). Haiku and 4.5-era models do NOT support adaptive and would 400 on `type "adaptive"` — set `:think-mode :off` for them.

### NO legacy budget path (Micah, 2026-06-25)
The deprecated `:thinking {:type "enabled" :budget_tokens N}` path is NOT implemented. No `budget_tokens`, no `thinking-budget-max` use, no max_tokens-vs-budget arithmetic. The adapter does adaptive-only. The file's EXISTING budget_tokens scenarios (effort 1->3200 etc.) are DELETED (they test the removed path), replaced by the adaptive scenarios below.

### Config / defaults
- Default `:think-mode` for the Anthropic provider = `:adaptive`.
- zanebot: claude.edn (sonnet-4-6) and opus.edn (opus-4-6) use adaptive (default). **haiku.edn (claude-haiku-4-5) must set `:think-mode :off`** (haiku is not adaptive-capable) — part of this fix's config changes.

Rationale: matches Anthropic's recommended/forward path, eliminates the budget-vs-max_tokens bug by construction, and maps the 0-10 knob to native categorical effort (consistent with the OpenAI adapter's low/med/high). opencode/qwen-code hit the same deprecated-path bug (Sources).

## Scenarios (approved 2026-06-25, Micah)
Land in `features/llm/api/messages/api.feature` (@wip), reusing existing steps (`the last outbound HTTP request matches:`, `the last provider request does not contain path`, `the isaac EDN file ... exists with:`, `the user sends ... on session` — NO new steps). DELETE the file's existing budget_tokens scenarios (removed path).

```gherkin
@wip
Scenario: adaptive thinking sends type adaptive and an effort level, no budget_tokens
  Given the isaac EDN file "config/models/grover.edn" exists with:
    | path       | value    |
    | model      | echo     |
    | provider   | grover   |
    | think-mode | adaptive |
  And the isaac EDN file "config/crew/main.edn" exists with:
    | path   | value  |
    | model  | grover |
    | effort | 7      |
  When the user sends "hi" on session "thinking"
  Then the last outbound HTTP request matches:
    | path                      | value    |
    | body.thinking.type        | adaptive |
    | body.output_config.effort | high     |
    | body.max_tokens           | 16000    |
  And the last provider request does not contain path "body.thinking.budget_tokens"

@wip
Scenario: effort 0 under adaptive sends no thinking block
  Given the isaac EDN file "config/models/grover.edn" exists with:
    | path       | value    |
    | model      | echo     |
    | provider   | grover   |
    | think-mode | adaptive |
  And the isaac EDN file "config/crew/main.edn" exists with:
    | path   | value  |
    | model  | grover |
    | effort | 0      |
  When the user sends "hi" on session "thinking"
  Then the last provider request does not contain path "body.thinking"
  And the last provider request does not contain path "body.output_config"
  And the last outbound HTTP request matches:
    | path            | value |
    | body.max_tokens | 16000 |

@wip
Scenario Outline: effort maps to the Anthropic adaptive effort level
  Given the isaac EDN file "config/models/grover.edn" exists with:
    | path       | value    |
    | model      | echo     |
    | provider   | grover   |
    | think-mode | adaptive |
  And the isaac EDN file "config/crew/main.edn" exists with:
    | path   | value    |
    | model  | grover   |
    | effort | <effort> |
  When the user sends "hi" on session "thinking"
  Then the last outbound HTTP request matches:
    | path                      | value   |
    | body.output_config.effort | <level> |

  Examples:
    | effort | level  |
    | 1      | low    |
    | 3      | low    |
    | 4      | medium |
    | 6      | medium |
    | 7      | high   |
    | 9      | high   |
    | 10     | max    |
```
(Legacy/budget scenario dropped — no legacy support. Reconcile the Given rows with the file's existing background when writing.)

## Acceptance
- Sonnet 4.6 (adaptive default): request body has `thinking.type "adaptive"` + `output_config.effort` mapped from 0-10; NO `budget_tokens`; `max_tokens` is a flat cap (default 16000). No `max_tokens must be > budget_tokens` 400.
- A fresh claude (sonnet-4-6) session at default effort takes a turn successfully end-to-end (the live regression).
- effort 0 -> no thinking block.
- NO budget_tokens anywhere; NO legacy `:enabled` path. `:think-mode :off` (e.g. haiku) sends no thinking, flat max_tokens.
- 0-10 -> level mapping per spec (1-3 low, 4-6 medium, 7-9 high, 10 max; xhigh skipped).
- haiku.edn sets `:think-mode :off`; sonnet-4-6/opus-4-6 use adaptive default.
- Existing budget_tokens api.feature scenarios DELETED; adaptive scenarios added (@wip) and green; effort.feature unaffected.

## Notes
Surfaced 2026-06-25 on zanebot: fresh sonnet-4-6 session failed every turn with `invalid_request_error: max_tokens must be greater than thinking.budget_tokens`. Micah declined the effort-0 config mitigation. Research (2026-06-25) found budget_tokens is deprecated on 4.6 -> adaptive thinking is the correct fix (option A). default-effort 7 being aggressive is a SEPARATE issue (not this bean).

Sources:
- Anthropic Adaptive thinking: https://platform.claude.com/docs/en/build-with-claude/adaptive-thinking
- Anthropic Extended thinking: https://platform.claude.com/docs/en/build-with-claude/extended-thinking
- Anthropic Effort: https://platform.claude.com/docs/en/build-with-claude/effort
- opencode #2055 / qwen-code #2508 (same deprecated-path bug): https://github.com/sst/opencode/issues/2055 , https://github.com/QwenLM/qwen-code/issues/2508
- OpenClaw Anthropic: https://docs.openclaw.ai/providers/anthropic
