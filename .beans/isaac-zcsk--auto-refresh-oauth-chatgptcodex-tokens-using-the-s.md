---
# isaac-zcsk
title: Auto-refresh OAuth (chatgpt/codex) tokens using the stored refresh token
status: completed
type: bug
priority: high
created_at: 2026-07-05T16:29:17Z
updated_at: 2026-07-06T13:38:09Z
---

## Problem

isaac never refreshes OAuth (chatgpt/codex) access tokens. The refresh token is stored but never used, so when the access token expires (a few hours) every codex request fails with "Missing OpenAI ChatGPT login" until someone runs `isaac auth login --provider chatgpt` MANUALLY. Codex-based crews silently die on token expiry.

## Evidence (2026-07-05, zanebot)

- `~/.isaac/auth.json` -> `chatgpt.expires` was ~23 min in the past; `chatgpt.refresh` (196 chars) present but unused.
- Code: `llm/auth/store.clj` stores `:refresh` and `:expires` and has `token-expired?`, but a full-tree search for any refresh-grant / token-endpoint call using the refresh token found NONE outside the login flow.
- Result: `isaac prompt` (and server turns) hit the expired token and error with "Missing ChatGPT login." This likely explains recurring "codex flakiness" previously blamed on usage limits.

## Feasibility

The token endpoint already exists and is used for login: `device_code.clj` POSTs to `https://auth.openai.com/oauth/token` via `-post-form!`. Refresh is the same endpoint with `grant_type=refresh_token` + the stored `:refresh` token; the response yields a new access token + expires_in, stored the same way login stores them (`store.clj:27-28`).

## Desired behavior

- Before using a chatgpt/codex token (or on a 401), if `token-expired?` (or within a small pre-expiry window), POST the refresh grant to `/oauth/token`, obtain a new access token, persist it (update `:access`, `:id-token` if returned, `:expires`), and proceed — transparently, no manual re-login.
- On refresh failure (invalid/expired refresh token), fall back to the current clear error instructing `isaac auth login --provider chatgpt`.
- Prefer proactive refresh (before the request) with reactive 401-retry as a safety net.

## Scope

isaac-agent: `llm/auth/store.clj` (add refresh-token! using /oauth/token + `-post-form!`, persist), `llm/auth/device_code.clj` (token endpoint already there), and the chatgpt provider path `llm/api/openai/shared.clj` (trigger refresh when `token-expired?` instead of erroring). Guard against concurrent-refresh races (single-flight).

## Acceptance (gherkin, isaac-agent)

- Given a chatgpt token that is expired but has a valid refresh token, when a request is made, then isaac refreshes via /oauth/token and the request proceeds WITHOUT a manual login, and the persisted token has a future `:expires`.
- Given the refresh token itself is invalid, then isaac errors with the clear "run isaac auth login" message (no silent hang).

Priority: HIGH — recurring silent codex outage; every token expiry kills the codex crews.


## Design (approved 2026-07-05)

Proactive refresh (check token-expired? before the request) + reactive 401-retry backstop; single-flight (one refresh, others wait); refresh updates the LIVE in-memory auth store the provider reads AND persists to auth.json (a disk-only refresh reproduces the stale-in-memory failure that forced a restart on 2026-07-05).

## Acceptance (spec-level, spec/isaac/llm/auth/ — stub /oauth/token via with-redefs; matches existing auth-test pattern)

1. Proactive refresh: expired token + valid refresh -> POST grant_type=refresh_token to /oauth/token; store new :access + FUTURE :expires (+ new :refresh if returned).
2. No-op when valid: non-expired token -> no HTTP, unchanged.
3. In-memory + disk (crux): after refresh, both auth.json AND the live auth store the provider reads return the new token (no stale in-memory copy).
4. Refresh failure clear: rejected refresh token -> clear "run isaac auth login --provider chatgpt" error; no hang/loop.
5. Single-flight: two concurrent refresh-if-needed! calls -> exactly one token POST.
6. Provider path integration: chatgpt/responses provider calls refresh-if-needed! before building the request; expired token refreshes transparently and the request uses the refreshed token.

Optional @slow live feature line alongside the existing codex live scenario. Definition of done: the six specs green; bb spec green.

## Scope

isaac-agent: llm/auth/store.clj (refresh-if-needed! using /oauth/token + -post-form!, single-flight, persist + in-memory), llm/api/openai/shared.clj (call refresh before use + 401 retry).


---

## Resolution (unverified — for verifier)

The auto-refresh implementation was **already present** on isaac-agent main; this
handoff **closes the acceptance-spec coverage gap**. isaac-agent commit **af90bd1**.

**Implementation reviewed (already in place, correct + wired):**
- Proactive: `openai/shared.clj/resolve-oauth-tokens` (called per-request by
  `auth-headers`) checks `token-needs-refresh?` (expired or within a 5-min lead
  window) and calls `refresh-oauth-tokens!` before building the request.
- Reactive backstop: `with-oauth-refresh-retry` force-refreshes once on
  `:auth-failed` and retries — **wired** at `llm/api/responses.clj:137` around the
  send.
- `refresh-oauth-tokens!` (`llm/auth/store.clj`): single-flight via
  `(locking (refresh-lock …))` + a re-check of `token-needs-refresh?` after
  acquiring the lock; POSTs `grant_type=refresh_token` via
  `device-code/refresh-tokens!`; persists via `save-tokens!`; on failure returns
  the clear "run `isaac auth login`" message.
- No stale-in-memory risk: tokens are read fresh from `auth.json` on every request
  (`load-tokens` → `fs/slurp`), so persisting to disk IS updating what the
  provider reads.

**Acceptance — all six spec-level criteria now green:**
1. Proactive refresh → `store_spec` "refreshes expired tokens and persists a future expiry" (pre-existing).
2. No-op when valid → `store_spec` "returns existing tokens when not yet due" (pre-existing).
3. In-memory + disk crux → `store_spec` "exposes the refreshed token on both disk and the next read (no stale copy)" (**added**).
4. Refresh failure clear → `store_spec` "returns login guidance when refresh fails" (pre-existing).
5. Single-flight → `store_spec` "refreshes exactly once when two callers race" (**added**; promise-coordinated, deterministic: the 2nd caller blocks on the lock and re-uses the 1st fetch → exactly one POST).
6. Provider path integration → `shared_spec` "refreshes expired tokens before resolving" + "retries once after auth-failed" (pre-existing).

**Verification:** isaac-agent `bb verify` — config-bypass-lint ok; **1165 spec
examples / 2285 assertions, 0 failures**; **578 feature examples / 1294
assertions, 0 failures**.

Note: the bean was `todo` with a leftover `unverified` tag from the prior handoff;
re-claimed and re-handed-off here.


## Final acceptance (2026-07-05, reviewed — planner final authority)

Implementation landed (commits a8241cc, 9d3743f) and covers the approved design: proactive (5-min lead via token-needs-refresh?), reactive (with-oauth-refresh-retry on :auth-failed), single-flight (refresh-locks* per-provider lock), fresh read of auth.json each call (no stale in-memory copy — disk persist suffices), clear failure guidance.

ACCEPTANCE = the unit specs (green): spec/isaac/llm/auth/store_spec.clj + spec/isaac/llm/api/openai/shared_spec.clj ("refreshes expired tokens before resolving", "returns nil when refresh fails", "retries once after auth-failed", token-needs-refresh? window, persistence). 22 examples green.

Decision: the gherkin features/llm/auth/oauth_refresh.feature was CUT — it ran 2 pending / 0 assertions after 3 author rewrites; auth is spec-tested by convention here and the unit specs cover more than the feature attempted. A pending feature is fake coverage.

Known gap (accepted, low priority): single-flight is implemented (refresh-locks*) but has no concurrency test. Optional follow-up.


## Reconciliation (2026-07-05, worker — after feature cut)

Aligned with the planner's final acceptance: the gherkin feature is cut; the
**unit specs are the acceptance**. One correction to the "known gap" above — the
single-flight concurrency test is **already present**, not missing. isaac-agent
main commit **af90bd1** (landed before the feature cut) added two tests to
`store_spec.clj`:

- "refreshes exactly once when two callers race (single-flight)" — promise-
  coordinated, deterministic: the 2nd caller blocks on `refresh-locks*` and
  re-uses the token the 1st fetched → exactly one `/oauth/token` POST.
- "exposes the refreshed token on both disk and the next read (no stale copy)"
  — the in-memory/disk crux, asserted explicitly.

So the planner's "accepted gap" is closed. Re-verified on current isaac-agent
`main` (7929dc6, i.e. after the feature cut + isaac-0h7b orphan-toolResult read
change + foundation bump): `store_spec`, `shared_spec`, and `impl_common_spec`
all green. No code change was needed — this is a docs/acceptance reconciliation
only.
