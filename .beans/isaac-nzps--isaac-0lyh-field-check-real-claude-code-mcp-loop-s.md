---
# isaac-nzps
title: 'isaac-0lyh field check: real Claude Code MCP loop smoke after 0.1.3 deploy'
status: completed
type: feature
priority: high
tags:
    - claude-cli
created_at: 2026-09-08T17:27:09Z
updated_at: 2026-09-08T17:40:11Z
parent: isaac-tuk1
---

Split from **isaac-0lyh**. Product + hermetic gates are green on `isaac-claude-code` `origin/bean/isaac-0lyh` @ `5b9d295` (`bb features features/llm/api/claude_driver.feature` 9/0/33; full `bb features` 23/0/81; `bb spec` 41/0/119 with 3 pending @real). Verify cannot complete 0lyh because acceptance required a live zanebot real-binary smoke that needs `isaac.llm.claude` **0.1.3** deployed. Verify skill forbids pin/release/deploy; live zanebot still has **0.1.0** @ `597c818` (nni3 rollback). Running the smoke against 0.1.0 would be a false pass (wrong parser).

## Field check (after 0lyh 0.1.3 is pinned and deployed on zanebot)

Do **not** run this against 0.1.0 / 0.1.2. The parser under test is 0.1.3 (`5b9d295` or its rebased/released equivalent).

1. Confirm `isaac modules list` shows `:isaac.llm.claude` **0.1.3** (or the released SHA that contains `5b9d295`).
2. Run:

       isaac prompt --crew scrapper --model claude-cli --session verify-mcp-smoke 'Use your exec tool to run exactly: echo mcp-loop-ok — then reply with only the command'\''s output.'

3. The reply is exactly `mcp-loop-ok`.
4. `cli.log` (or the session's server log) shows:
   - `:turn/loop-driver :driver :provider`
   - `:claude/driver-exit :exit-code 0 :result-event true`
   - **no** `:claude/driver-fallback`
5. Record evidence on this bean: module SHA/version, prompt session id, reply text, and the three log facts.

## Notes

- Do **not** reopen 0lyh product/parser work unless this field check fails.
- Blocked by 0lyh landing + train pin of 0.1.3 + deploy (`modules upgrade`; human operates the service lifecycle).
- Draft until a human promotes it after the train is on zanebot.
- Isolated `/tmp` roots and second Isaac services are **not** a substitute: the failure mode is the real CLI + MCP under `--strict-mcp-config` with the live keychain.



## Field check run by the planner (2026-09-08 17:35Z) — 0.1.3 (5b9d295) deployed
`isaac prompt --crew scrapper --model claude-cli --session train-mcp-smoke '…echo mcp-loop-ok…'` → reply `mcp-loop-ok` — but via the FALLBACK: cli.log shows `:claude/driver-exit :exit-code 0 :events {} :result-event false :stderr nil` then `:claude/driver-fallback :reason :cli-error`, then the fence path ran the tool. So 0lyh's fallback works (the turn no longer dies), but the native MCP loop still does not: with the driver's spawn the CLI exits 0 in ~1 s having printed NOTHING on stdout or stderr. A manual probe of the same flags WITHOUT --mcp-config (run through scrapper's exec tool inside the server) streams normally. Probe WITH a strict mcp-config in progress.



## Summary of Changes (planner, 2026-09-08 17:50Z)
Field check executed on 0.1.3 (5b9d295, deployed 17:33Z): the turn answers `mcp-loop-ok` but only via the fence FALLBACK — the driven spawn exits 0 with no output. Root causes found and filed as isaac-6z4r: stdin lines lack the stream-json user envelope (probe C reproduces the silent exit), and no --mcp-config is ever written. Field check therefore FAILS for the native loop; the fallback contract from 0lyh holds. Closed in favour of isaac-6z4r, whose acceptance carries the field check.
