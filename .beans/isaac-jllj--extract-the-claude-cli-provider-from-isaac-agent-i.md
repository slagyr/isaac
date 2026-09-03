---
# isaac-jllj
title: Extract the claude-cli provider from isaac-agent into the isaac-claude-code module (pure move)
status: in-progress
type: task
priority: high
tags:
    - claude-cli
    - module
created_at: 2026-09-03T23:07:34Z
updated_at: 2026-09-03T23:08:22Z
parent: isaac-tuk1
---

PURE MOVE. Byte-identical behavior. No improvements, no renames beyond the namespace move, no scenario edits beyond path moves. If anything needs changing to make it work, stop and hail plan.

## What moves (isaac-agent → isaac-claude-code, repo https://github.com/slagyr/isaac-claude-code, empty)
- `src/isaac/llm/api/claude_cli.clj` → same namespace `isaac.llm.api.claude-cli` in the module (keeps every existing require/model config working).
- `features/llm/api/claude_cli.feature`, `spec/isaac/llm/claude_cli_spec.clj`, `spec/isaac/llm/claude_cli_real_spec.clj`, `spec/isaac/llm/claude_cli_steps.clj`.
- Manifest contributions, moved verbatim from resources/isaac-manifest.edn into the module manifest: llm-api `:claude-cli {:factory isaac.llm.api.claude-cli/make}` (line ~105); provider-template `:claude {:template {:api "claude-cli" :auth "none" :command "claude" :stream-supports-tool-calls false}}` (~169); provider schema keys `:stream-non-tool-turns`, `:command`, `:extra-args` (~277-286) — via the module's extra-schema mechanism (see isaac-imessage's :extra-schema and isaac-m4bi for how modules contribute schema).
- The `(= provider "claude") "claude-cli"` default in `src/isaac/llm/api/protocol.clj:153` and its spec in `spec/isaac/llm/providers_spec.clj:74-76`: keep the core behavior identical — either leave the mapping in core (it is a string default, harmless without the module) or move it to the module's provider-template; do NOT change the observable default. Prefer leaving it.

## Module skeleton
Copy isaac-imessage's layout (deps.edn with :dev-local + :spec aliases, bb.edn tasks spec/jvm-spec/features/jvm-features/ci/smoke, AGENTS.md, README, LICENSE). Module id `:isaac.llm.claude`, manifest at `src/isaac-manifest.edn`, version 0.1.0, `:factory isaac.module.protocol/module`. Pin isaac-agent at the SHA that removes the in-tree copy (see train rule).

## Train rule (same as scuttlebutt)
The agent release that deletes the in-tree provider and the module's first release must be pinned together in isaac/modules.edn (add `:isaac.llm.claude` to the registry) and upgraded on zanebot in one train, or every crew on :claude-opus / :claude-cli (ratchet, main, prowl, scrapper's fallback) loses its provider. Note both SHAs on this bean. Zanebot config needs no change (`providers/claude.edn` is `{:type :claude}`, resolved via the template).

## Acceptance
- isaac-claude-code: `bb features` (claude_cli.feature green, unchanged rows) and `bb spec` green; the @real spec stays gated as it is today.
- isaac-agent: `bb features` and `bb spec` green with the four files gone and the three manifest entries removed; `grep -r claude-cli src` shows only the protocol.clj default (if left).
- `isaac modules list` on a root with both pinned shows :isaac.llm.claude ok and `isaac config validate` accepts a `{:type :claude}` provider.
- Smoke after the train: `isaac prompt --crew main --session train-pong "Reply with exactly: pong"` on zanebot (main is on :claude-opus).


## Work observations (2026-09-03, scrapper)

Conflict detected before implementation handoff:

- The bean says the move must be PURE / byte-identical and says to stop if anything else must change.
- The bean also says the `:claude` provider-template manifest entry should move out of `isaac-agent`, while separately saying the existing core behavior at `src/isaac/llm/api/protocol.clj:153` / `spec/isaac/llm/providers_spec.clj:74-79` must remain observable and preferably stay in core.
- On current `isaac-agent` main, `spec/isaac/llm/providers_spec.clj` directly asserts `(sut/template "claude")` returns `{:api "claude-cli" :auth "none" :command "claude" :stream-supports-tool-calls false}` with no module index. Removing the core provider-template entry makes that spec/behavior fail unless we either (a) keep the `:claude` template in core, or (b) change the spec / behavior to require module loading.
- Option (a) violates the stated acceptance text `the three manifest entries removed`; option (b) violates the PURE MOVE / no-behavior-change rule.

Planner decision needed: either keep the built-in `:claude` provider-template (and any needed provider schema keys) in `isaac-agent` for this extraction bean, or explicitly authorize the behavior/spec change away from `(sut/template "claude")` in core.
