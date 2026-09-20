---
# isaac-zz6d
title: Verify turns on Opus 5 end with zero tool calls and report delivered, stranding every ungated bean
status: todo
type: bug
priority: high
tags:
    - hail
    - ops
created_at: 2026-09-20T06:30:48Z
updated_at: 2026-09-20T06:30:48Z
---

Found 2026-09-20 05:20–06:35Z on zanebot while driving the Bean Gate train (isaac-rmq6).

## Symptom

Verify turns end immediately with **zero tool calls**. The model emits one sentence — "I'll load the verification skill once and begin on `isaac-przv`." / "I'll load the verification skill first." — with `:stopReason :end-turn`, and the hail is marked delivered. The bean is left exactly as it was: `in-progress` + `unverified`, stranded.

| hail | session | result |
|---|---|---|
| b96bd90f | isaac-verify | one sentence, 0 tools, `:outcome :delivered` |
| 1d18a9fe | isaac-verify | one sentence, 0 tools, delivered (7s) |
| d95b27e8 | isaac-verify | prompt override demanding a tool call first → `:error :empty-terminal-response` ("model returned no content after continuation retry"), 3 attempts |
| 483b3707 | isaac-verify-2 | one sentence, 0 tools, delivered |

## Not the obvious things

- **Not context.** isaac-verify sat at 7% of its window (18K/200K) for three of these.
- **Not tools.** `crew/perceptor.edn` allows `:fs/* :exec/run :web/* :memory/* :skill/* :hail/* :comm/send`, directories `:cwd`.
- **Not session-specific.** Both isaac-verify and isaac-verify-2 behave the same.
- **Not the provider generally.** Worker sessions on the *same* model and provider (scrapper, `:claude-opus-5` over claude-code) did full multi-tool turns in the same window — isaac-przv's worker exercised a whole scratch harness.

The variable that changed: perceptor moved from `:grok-4-6` to `:claude-opus-5` at 2026-09-19 23:33Z. Every verify turn since has been a no-op.

## Why it matters

The ungated path is the only path for beans with no `feature-baseline`, which is still most of them. Six beans were queued `unverified` while this was happening (isaac-przv, isaac-7rce, isaac-8s6s, isaac-ddls and others). A verify hail that returns `:delivered` having done nothing is worse than a failure: the planner watch sees a delivered turn and waits.

## Where to look

- The verify band prompt (`~/.isaac/config/hail/isaac-verify.md`) ends "Load the skill once at the start of this turn." The work band says the same and its turns act — so compare what the two prompts plus crew souls produce, and whether the model is ending the turn where the work band keeps going.
- The claude-code provider's continuation handling: `:empty-terminal-response` after a "continuation retry" suggests Isaac asked for more and the CLI returned nothing. A turn that ends with no tool calls and no work is arguably a turn Isaac should retry with a nudge rather than report as `:delivered`.
- Consider making "delivered with zero executed tools and no bean-state change" a loud event; today it is indistinguishable from success in the hail log.

## Acceptance

Scenarios (worker writes, isaac-agent or isaac-hail as the seam decides): a hail-driven turn that ends with no tool calls and no output beyond a lead-in is not reported as `:delivered`; the drive nudges once before giving up, and the give-up is visible. Plus whatever the root cause turns out to need.

Workaround while this is open: move perceptor back to a model that acts, or let the planner verify and land (what happened to isaac-przv, main-sha 0eb8bc79).
