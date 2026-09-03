---
# isaac-1sdl
title: 'Loop-driver seam: the drive selects who runs the tool loop (isaac or the provider)'
status: draft
type: feature
tags:
    - claude-cli
    - drive
created_at: 2026-09-03T23:07:34Z
updated_at: 2026-09-03T23:07:34Z
parent: isaac-tuk1
blocked_by:
    - isaac-jllj
---

Today drive/turn.clj execute-llm-turn! calls `tool-loop/run chat-fn followup-fn request tool-fn {:max-loops :cancelled? :after-tools :on-cycle}` and gets `{:response :tool-calls :token-counts}`. Make that call go through a LoopDriver seam (protocol or multimethod in isaac.llm.tool-loop, chosen by a provider capability e.g. `(api/config p) :drives-tool-loop?`): the default impl is today's loop, unchanged. A provider-driven impl receives the same request + the same tool-fn (record-tool-call!, so toolCall/toolResult persistence, comm events and cancellation stay in the drive) + the same hooks, and must return the same result shape with per-cycle usage. Decisions to review with Micah: (a) after-tools mid-turn compaction is not available to a provider-driven loop — options: abort+compact+rerun the turn, or provider-driven loops compact only between turns; (b) :max-loops maps to the provider's own budget (claude --max-turns). No behavior change for existing providers. Scenarios (@wip, features/llm/tool_loop_driver.feature): (1) a provider declaring :drives-tool-loop? gets its driver invoked with the drive's tool-fn and the tool pair is persisted exactly as the default path persists it; (2) a provider without the flag runs the default loop (regression: existing tool_loop features unchanged); (3) the driver's per-cycle usage stamps last-input-tokens after each cycle (ties to isaac-vuto decision 1); (4) cancellation reaches the driver and the turn ends :cancelled. Draft until reviewed.
