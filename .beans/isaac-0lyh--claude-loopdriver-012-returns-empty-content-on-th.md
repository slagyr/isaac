

## Planner adjustment (2026-09-08, prowl@isaac-plan) — split the live real-binary smoke; hermetic gates control verify

Conflict: implementation on `origin/bean/isaac-0lyh` @ `5b9d295` is green on every hermetic gate, but the bean's acceptance also requires a verifier-owned live zanebot smoke that cannot run until `isaac.llm.claude` **0.1.3** is pinned and deployed. Verify skill forbids pin/release/deploy. Live zanebot is still **0.1.0** @ `597c818` (nni3 rollback). Running the smoke against 0.1.0 would be a false pass (wrong parser). Isolated `/tmp` roots and a second Isaac service are **not** a substitute: the failure mode is the real CLI + MCP under `--strict-mcp-config` with the live keychain.

**Decision: option 2 — drop the live smoke from this bean's verify acceptance.** Do not have the planner pin/deploy 0.1.3. Do not authorize a non-live 5b9d295 load as a substitute for the smoke.

The live smoke is now owned by **isaac-nzps** (draft, parent **isaac-tuk1**). That bean runs **after** 0lyh lands, 0.1.3 is pinned, and a human deploys it. 0lyh product/parser work is not reopened unless nzps fails.

### Acceptance (supersedes the acceptance block above)

Hermetic, verifier-runnable, no deploy:

    cd isaac-claude-code
    bb features features/llm/api/claude_driver.feature
    bb features
    bb spec

0 failures on each. The two planted 0lyh scenarios in `claude_driver.feature` are un-`@wip`; the other 7 driver scenarios are unchanged. Module version on the bean branch is 0.1.3. Registry pin of 0.1.3 remains a **train step**, not a verify step.

Do **not** require:
- `isaac prompt --crew scrapper --model claude-cli --session verify-mcp-smoke …`
- cli.log `:turn/loop-driver` / `:claude/driver-exit` / absence of `:claude/driver-fallback` on live zanebot
- pin, release, or deploy of 0.1.3
- any mutation of `~/.isaac/config`

### Still required of the implementation (already claimed green)

1. Log `:claude/driver-exit :exit-code N :result-event bool :stderr <head> :events {type counts}` for every driven spawn.
2. Assemble the reply from `stream_event`/`content_block_delta`/`text_delta` and `message` events; usage from the result event.
3. A result event with `is_error`, or an exit with no result event, → fence-path fallback with `:claude/driver-fallback :reason :cli-error`.
4. Fake CLI kinds `text_delta` and `error_result` so `bb features` exercises the 2.1 shapes.

The live confirmation of (1)–(3) against Claude Code 2.1.236 + MCP is **isaac-nzps**, not this bean.
