---
# isaac-98vy
title: CLI config commands fail validating provider :api — manifest berths not registered before validation (server boot OK)
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-06-22T19:32:55Z
updated_at: 2026-06-22T20:49:07Z
---

Any CLI command that loads config (`isaac crew list`, `config get`, `config set`) throws `invalid configuration … unknown berth: :isaac.agent/llm-api … valid-values []` when a provider's `:api` is validated. The SERVER boots the exact same config fine — so it's a CLI-path-only bug.

## Root cause
Provider config validates `:api` with `[:registered-in? :isaac.agent/llm-api]` (agent manifest). That berth is populated by the foundation module loader's manifest-berth processing (`process-manifest-berths!`), which the SERVER boot runs (isaac-server app/start!). The CLI config-load path (`isaac.config.loader/load-config!` -> `load-config-result`, used by `isaac.crew.cli/resolve-crew` etc.) validates WITHOUT first processing the agent's manifest berths, so `:isaac.agent/llm-api` is empty/unknown and EVERY `:api` value fails.

## Evidence (zanebot, foundation 0.1.7, agent 20fb5dd)
- `config/providers/chatgpt.edn` = `{:type :chatgpt}`; the chatgpt provider template defaults `:api "responses"`.
- `isaac crew list` throws on `providers.chatgpt.api` -> `unknown berth :isaac.agent/llm-api, valid-values []`.
- Setting `:api chat-completions` (or any value) fails identically — confirming the BERTH is empty in the CLI, not a bad value.
- The agent manifest DOES declare the berth with messages/chat-completions/ollama/grover/responses — and the server boots this config (proven: 09:00 reboot + JVM cutover both came up with discord+imessage). So the berth IS available at server-boot validation, NOT in the CLI.
- Not fixable by deploying a newer agent: `20fb5dd..151330a` only changes protocol.clj by REMOVING a log line; berth-registration code is identical.

## Fix direction
Make the CLI config-load/validation path register manifest berths (or at least the schema-relevant ones) before validating — i.e., share the server's berth-processing step, OR defer/relax `:registered-in?` validation when the contributing module's berths haven't been processed in a CLI context. Pin down where load-config! must call process-manifest-berths! (foundation), and confirm parity between CLI and server config-load.

## Acceptance
- `isaac crew list` / `config get` / `config set` succeed against a config whose providers set `:api` validated by `:isaac.agent/llm-api` (incl. the chatgpt `responses` default).
- CLI and server config validation produce identical results for the same config.
- Regression spec covering a provider `:api` validated against a module-contributed berth, loaded via the CLI path.

## Notes
Surfaced 2026-06-22 on zanebot. Foundation-level (CLI load path) fix; ships in a foundation release. The running server is unaffected and restarts safely; only CLI config commands are blocked.



## Worker notes (work-2)

Root cause: `check-resolved-providers` (agent config check) calls `validation/annotation-errors*` without binding `registered-in/*module-index*`, so inherited provider `:api` values fail `[:registered-in? :isaac.agent/llm-api]` on CLI load even though agent manifest is on the classpath.

Fix: foundation `isaac.config.check-compose/run-checks` now binds the same registered-in context as `semantic-errors`.

Foundation: 1785528409339cb9426c54f7421aa903378f4ebb
Agent: 311bb02 (regression spec + foundation pin bump)
