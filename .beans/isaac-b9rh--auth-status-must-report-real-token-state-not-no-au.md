---
# isaac-b9rh
title: auth status must report real token state (not no auth required for an expired OAuth token)
status: todo
type: bug
priority: normal
created_at: 2026-07-05T16:29:17Z
updated_at: 2026-07-05T17:18:57Z
---

## Problem

`isaac auth status` prints "no auth required" for the chatgpt provider even when it holds an OAuth token — including an EXPIRED one. The real state (authenticated / expired / expiring-soon / not-logged-in) is hidden, so a token-expiry outage is invisible in the status view.

## Evidence (2026-07-05)

- `auth.json` chatgpt token was expired, yet `isaac auth status` reported `chatgpt: no auth required`. This misled diagnosis of a live codex outage (the operator sees "no auth required" and rules auth out).
- Source: `llm/auth/cli.clj:121` prints `"  <name>: no auth required"` for providers, without inspecting the stored token / expiry.

## Desired behavior

Per provider, auth status reports the actual state:
- `authenticated (expires in Nh)` when a valid token is present,
- `EXPIRED — run isaac auth login --provider <name>` when the stored token is past `:expires`,
- `expiring soon (Nm)` optionally,
- `not logged in` when no token,
- `no auth required` ONLY for providers that genuinely need none (e.g. a keyless local provider).

Use `token-expired?` (store.clj) and the stored `:expires` to compute the state.

## Scope

isaac-agent: `llm/auth/cli.clj` (status printer ~line 121) + `llm/auth/store.clj` (expose token state). Pairs with the token auto-refresh bean.

## Acceptance (gherkin, isaac-agent)

- Given an expired chatgpt token, when `isaac auth status` runs, then it reports EXPIRED (not "no auth required").
- Given a valid token, it reports authenticated with time-to-expiry.
- Given a genuinely keyless provider, it still reports "no auth required".

Priority: NORMAL — observability; the misreport turned a clear failure into a confusing one.


## Design (approved 2026-07-05)

`isaac auth status` must report real per-provider state, derived from the provider's :auth requirement + the stored token / `token-expired?` — not a blanket "no auth required".

States: `authenticated (expires in Nh)` (valid OAuth) / `EXPIRED — run isaac auth login --provider <name>` (past :expires) / `authenticated (API key)` (keyed provider with key) / `not logged in` (needs auth, no token) / `no auth required` (only genuinely keyless providers, e.g. ollama).

## Acceptance (spec-level, spec/isaac/llm/auth/cli_spec.clj pattern)

1. Expired chatgpt OAuth token -> reports EXPIRED (not "no auth required").
2. Valid OAuth token (future :expires) -> authenticated with time-to-expiry.
3. OAuth provider, no token -> not logged in.
4. API-key provider with a key -> authenticated (API key).
5. Genuinely keyless provider -> no auth required.

Definition of done: the five specs green; bb spec green. Pairs with isaac-zcsk (auto-refresh).

## Scope

isaac-agent: llm/auth/cli.clj (status printer ~line 121) + llm/auth/store.clj (expose token state / expiry).
