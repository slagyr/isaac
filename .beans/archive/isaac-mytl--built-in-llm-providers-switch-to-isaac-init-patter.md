---
# isaac-mytl
title: "Built-in LLM providers: switch to -isaac-init pattern"
status: completed
type: task
priority: low
created_at: 2026-05-07T04:19:40Z
updated_at: 2026-05-07T05:28:51Z
---

## Description

Built-in LLM providers (anthropic, claude-sdk, grover, ollama, openai-compat) currently register via 'defonce _registration' that fires as a side effect when isaac.drive.dispatch requires the namespace. Comms moved to an explicit '-isaac-init' activation hook in isaac-au48; align built-in providers with the same pattern so they're ready to become modules later without changing the registration code itself.

Approach:
- Replace each LLM's '(defonce _registration (provider/register! ...))' with '(defn -isaac-init [] (provider/register! ...))'
- Add an explicit boot call point that invokes -isaac-init for each built-in (likely in isaac.drive.dispatch since it already :requires all 5, or at server boot — pick one).
- When a provider eventually moves to a module, the eager :require disappears and module.loader/activate! invokes the same -isaac-init.

Files:
- src/isaac/llm/anthropic.clj
- src/isaac/llm/claude_sdk.clj
- src/isaac/llm/grover.clj
- src/isaac/llm/ollama.clj
- src/isaac/llm/openai_compat.clj
- src/isaac/drive/dispatch.clj (or wherever boot calls land)

## Acceptance Criteria

Each built-in LLM provider defines -isaac-init and no longer uses defonce _registration; a single explicit boot point calls all five; bb spec and bb features green; behavior unchanged.

## Notes

If sub-bead isaac-4ca2 (LLM reorg A) lands first, the worker should fold this -isaac-init switch into that work — same five files, same registration code. Otherwise this stays a small standalone cleanup.

