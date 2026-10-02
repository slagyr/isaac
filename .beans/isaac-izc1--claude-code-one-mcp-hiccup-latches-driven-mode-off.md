---
# isaac-izc1
title: 'claude-code: remove the fence fallback; a driven-mode failure is weather'
status: in-progress
type: bug
priority: high
created_at: 2026-10-02T04:41:33Z
updated_at: 2026-10-02T14:43:34Z
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

## Contract conflict (2026-10-02, scrapper@isaac-work-1)

The frozen `claude_driver.feature` contract still requires the setting which the approved acceptance explicitly removes. Background lines 23-26 put `drives-tool-loop? | true` into `config/providers/claude-code.edn`; scenario at line 432 puts the same key in the harbor provider, and the two-provider scenario at lines 635-652 puts it in both provider configs. The feature's introductory text also states `:drives-tool-loop? true`. These are in the baselined header, Background and scenarios; the gate allows only `@wip` removal. Removing the key from the manifest/schema would cause those scenario configs to be unrecognized/pruned or warned, while preserving the key violates the explicit clean-cutover requirement. Planner must revise these blocks on module main and re-baseline before implementation can honestly satisfy both contracts. No product code or feature changes were made.

feature-baseline: isaac-claude-code 54fd69e68fdbb79299e494d311c0f185deb406d1
feature-blob: isaac-claude-code features/llm/api/claude_driver.feature 5404d7e5560ee2f61f82ecf35c8a5361ee648367
feature-blob: isaac-claude-code features/llm/api/claude_cli.feature 32fb55b6696dc93f07227534c05beecd3c146456

## Planner adjustment (2026-10-02, prowl@isaac-plan)

Clean cutover stands. The setting is gone from the frozen feature.

On isaac-claude-code main `54fd69e`, `claude_driver.feature` no longer sets `drives-tool-loop?`. Dropped from the background, the harbor provider, and both providers in the two-provider scenario. The header no longer says the setting is required, and no longer describes a fence fallback. The five `@wip` scenarios stay `@wip`. `claude_cli.feature` was already free of the setting; its blob is unchanged.

Re-baselined onto `54fd69e`. Driver blob `5404d7e5`. CLI blob `32fb55b6`. Rebase onto `54fd69e`. Drop `@wip` only. Do not edit frozen scenario text.

Contract fix (planner, 2026-10-02): the frozen claude_driver.feature still set `drives-tool-loop? true` (background + 3 scenarios + description) while acceptance removes the setting — the worker rightly handed off. Planner removed those rows on main (driven is the only mode); re-baselined below.

feature-baseline: isaac-claude-code 54fd69e68fdbb79299e494d311c0f185deb406d1
feature-blob: isaac-claude-code features/llm/api/claude_driver.feature 5404d7e5560ee2f61f82ecf35c8a5361ee648367
feature-blob: isaac-claude-code features/llm/api/claude_cli.feature 32fb55b6696dc93f07227534c05beecd3c146456

## Worker checkpoint (2026-10-02, scrapper@isaac-work-3)

Done: committed claude-code worktree branch `bean/isaac-izc1` at 7a5c429; agent branch committed, rebased and `bb ci` green (1864 specs, 843 features), squash committed on agent main as 2eb199706f7b1e89b9db54dd76f130e498c83cb4 (not pushed). Claude-code `bb ci` was green against agent branch before rebasing/landing; `bb bean-gate verify isaac-izc1 --dir isaac-claude-code=../isaac-claude-code-izc1 --dir isaac-agent=../isaac-agent-izc1` returned PASS.
Next: STOP, planner must reconcile the frozen claude_driver.feature with newly landed isaac-o13p behavior. After agent rebase onto origin/main 9ff7add, clean claude-code branch's `clojure -Sdeps '{:aliases {:features {:override-deps {io.github.slagyr/isaac-agent {:local/root "../isaac-agent-izc1"} io.github.slagyr/isaac-agent-spec {:local/root "../isaac-agent-izc1/spec"}}}}}' -M:features` is RED: 59 examples, 4 failures in `features/llm/api/claude_driver.feature:52, :353, :575, :627`. Frozen assertions expect `last-input-tokens` 260 and 22378 (first-cycle gauge); new agent code `../isaac-agent-izc1/src/isaac/agent/drive/turn.clj:295-313` (isaac-o13p) explicitly makes valid FINAL cycle report win over first-cycle gauge; actual 320, 370, 320, 39765. This is a contract conflict, not safe to change .feature text or reverse unrelated upstream semantics. Await planner re-baseline or explicit direction. Agent main squash is LOCAL ONLY, not pushed; branch retained.
