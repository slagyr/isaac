---
# isaac-izc1
title: 'claude-code: remove the fence fallback; a driven-mode failure is weather'
status: todo
type: bug
priority: high
created_at: 2026-10-02T04:41:33Z
updated_at: 2026-10-02T14:08:54Z
---

Found 2026-10-02 on yopp (agent ce1913c, claude-code ad434cb). A yopp ACP turn ended `:error :llm-error` on "You've hit your session limit · resets 5am (UTC)" and Isaac announced the provider broken; it was never parked/resumed.

claude-code recognizes that text (`limit-failure-re`, claude_cli.clj:369-372) and `cli-weather`/`weather-kind` (1032-1053) would classify it `{:unavailable? true :reason :wall}`, but only when `driven?` (line 1096). `driven?` is gated on the process-wide `defonce fail-mcp-init?*` (line 52), set by `fence-fallback!` on any MCP hiccup and never reset in production. The same session logged `:claude/driver-fallback :reason :mcp-failed` at 04:09:22Z — from then on, for every session in the process, no wall classification and no native tool calls (see the pseudo-tool-call bean).

## Wanted
- Driven-mode fallback is per-session or self-healing (retry driven mode next turn), never a permanent process latch.
- Wall classification (`cli-weather`) runs regardless of driven mode.
## Acceptance (scenarios TBD)
- After an unrelated MCP fallback, a CLI result reporting "session limit"/"resets 5am" (and the weekly variant) classifies as `:wall` with retry-at, parks the turn and auto-resumes — not `:llm-error`.

## Re-scope (Micah, 2026-10-02): remove the fallback and fence mode

The automatic driven→fence fallback should not exist: it swaps the reliable protocol (native tool calls over MCP) for a fragile one (hand-written tool-call text parsed back) exactly when something has gone wrong, hides the failure, and leaves a second mode nobody exercises to rot (weather, streaming and usage accounting already break in it). Nothing configures `:drives-tool-loop? false` (template default true; zanebot/yopp do not override; only one claude-code scenario uses it).

New intent:
- A driven-mode failure is provider weather: MCP not coming up, or the CLI erroring before a result, suspends the turn with reason `:mcp-unavailable` and retries in driven mode on the normal weather backoff. No process-wide switch.
- Usage limits / expired login classify as weather (`:wall` / `:auth`) unconditionally.
- Persistent MCP failure escalates: after repeated failed retries, an attention notice is raised (deliverable to gchat once isaac-ixcm lands).
- Remove fence mode entirely: `fail-mcp-init?*`, `fence-fallback!`, the fence retry, the `driven?` conditionals in `invoke!`, the textual tool-call contract and its parser, and the `:drives-tool-loop?` setting (clean cutover).
- Tool-less completions (episode gists/summaries on a claude-code model) keep working as plain completions without MCP.

Scenarios to be redrafted to this scope before baseline.

## Acceptance (Micah approved 2026-10-02; gated)
- The 5 @wip scenarios in isaac-claude-code `features/llm/api/claude_driver.feature` (MCP failure is weather :mcp-unavailable; resume in driven mode; weekly limit is :wall; repeated MCP failure raises attention; tool-less completion runs without --mcp-config) pass with @wip removed.
- The 18 @wip plain-completion scenarios in `features/llm/api/claude_cli.feature` (background no longer sets drives-tool-loop? false) pass with @wip removed, on the tool-less path.
- Planner retired 13 fence-mode scenarios in the planning commit (8154947): 4 fallback scenarios in claude_driver.feature, 9 text-protocol scenarios in claude_cli.feature.
- Remove fail-mcp-init?*, fence-fallback!, the fence retry, driven? conditionals, the textual tool-call contract + parser, and the :drives-tool-loop? setting (manifest/schema). Handbook chapter updated. `bb ci` + jvm-spec/jvm-features green.

feature-baseline: isaac-claude-code 815494789d835a84cdbb431b8359b65b815dff70
feature-blob: isaac-claude-code features/llm/api/claude_driver.feature a8aceb512ef4142282ec3c36f2dc63a62ac25ea9
feature-blob: isaac-claude-code features/llm/api/claude_cli.feature 32fb55b6696dc93f07227534c05beecd3c146456
