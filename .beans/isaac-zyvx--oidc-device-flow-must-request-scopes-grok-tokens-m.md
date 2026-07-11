---
# isaac-zyvx
title: OIDC device flow must request scopes — grok tokens minted without api:access are useless
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-07-11T00:55:25Z
updated_at: 2026-07-11T03:23:21Z
---

## Bug

request-user-code! sends only client_id to the device endpoint. auth.x.ai grants a token with default (empty) scope, and every api.x.ai call then fails: 403 {"code":"permission-denied","error":"OAuth2 token missing required scope: api:access"}. Login LOOKS successful (tokens persisted) but the provider is unusable.

## Fix

- OAuth descriptor gains :scope; grok descriptor + :grok manifest template set "api:access offline_access" (probe-verified 2026-07-11: device endpoint accepts it and issues device_code/user_code).
- Device request includes scope for :flow :oidc-device-code (form-encoded, per isaac-88ol). OpenAI flow unchanged.
- SECONDARY (same bean): the 403 surfaced as an EMPTY SUCCESSFUL turn in isaac prompt ({"response":""} exit 0) — a provider HTTP error on the streaming path must surface as an error (and classify :reason :auth per isaac-5a4n on hail turns), never as empty text. Find and fix the swallow.

## Scenario coverage (worker)

- `device_code_spec`: OIDC device request form body includes `scope` (`api:access offline_access`); chatgpt JSON request has no scope.
- `provider_walls.feature`: 403 permission-denied → unavailable reason auth.
- `cli-prompt.feature`: grover 403 http-error → exit 1, stderr contains API message (not empty JSON success).
- Manifest + `grok-descriptor` carry `:scope`.

## Worker notes

- Repo: `isaac-agent` `bean/isaac-zyvx`.
- `provider-wall/normalize` runs immediately after `tool-loop/run` so SSE HTTP errors classify before empty-terminal guard; `prompt_cli` treats `:unavailable?` as failure.

## Post-deploy note

Existing grok store tokens are scope-less and must be discarded: Micah re-runs isaac auth login --provider grok once after this ships (attempt #5, the one that works).


## Verify fail (attempt 1, 2026-07-11): branch acceptance scenarios pass in isolation, but the branch cannot pass verification because spec/isaac/llm/auth/device_code_spec.clj has an unmatched delimiter and breaks the canonical spec/CI gate

Evidence:
- Canonical implementation is `isaac-agent` `origin/bean/isaac-zyvx` at `a770371`, based on current `origin/main` `92775a4` (`git merge-base --is-ancestor origin/main origin/bean/isaac-zyvx` -> exit 0).
- Bean acceptance scenarios themselves are present and green when targeted:
  - `clojure -M:features features/bridge/cli-prompt.feature:16 features/llm/provider_walls.feature:75` -> `2 examples, 0 failures, 5 assertions`.
  - Full feature suite rerun also went green: `clojure -M:features` -> `623 examples, 0 failures, 1430 assertions`.
- However, the branch does **not** satisfy the bean's required CI gate because the spec source is malformed:
  - `clojure -M:spec` on the bean branch exits `255` with `Syntax error reading source` / `Unmatched delimiter: )` at `spec/isaac/llm/auth/device_code_spec.clj:396:105`.
  - `bb ci` therefore fails immediately after `config-bypass-lint: ok` with exit `255`.
- The regression is branch-specific, not a pre-existing mainline failure:
  - `clojure -M:spec` on current `isaac-agent` `origin/main` exits `0` (`1212 examples, 0 failures, 2417 assertions`).
- The malformed file is inside the bean diff (`origin/main..origin/bean/isaac-zyvx`), so the worker must fix the broken spec file on the bean branch and rerun `bb ci`.


## Worker resume (isaac-work-2, 2026-07-11)

- Checked out isaac-agent bean/isaac-zyvx at d6bb586 (spec delimiter fix already on branch).
- bb ci green: 1214 specs + 623 features, 0 failures.
- origin/bean/isaac-zyvx up to date; re-handoff to verify.
