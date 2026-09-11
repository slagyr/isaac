---
# isaac-b9rh
title: ""
status: completed
type: task
priority: normal
created_at: 2026-07-06T14:33:41Z
updated_at: 2026-07-06T14:50:51Z
---


---

## Resolution (unverified — for verifier)

Implemented in isaac-agent `main` commit **47ed3e4**. (Bean was `in-progress` via a
planner placeholder claim with no implementation; took it over and built it.)

**`llm/auth/cli.clj`** — the `status` printer no longer hardcodes "no auth
required" for every non-anthropic provider. New pure `provider-auth-line
[name p tokens now-ms]` classifies each provider from its `:auth` requirement +
the stored token:
- `:auth "oauth-device"` (or a stored `:type "oauth"` token): `not logged in`
  (no token) / `EXPIRED — run isaac auth login --provider <name>` (past
  `:expires`) / `authenticated (expires in Nh)` (valid; `Nh`/`Nm` humanized).
- api-key (has `:api-key`, or `:auth "api-key"`, or a stored api-key token):
  `authenticated (API key)` / `not logged in`.
- `:auth "none"` (ollama/grover): `no auth required`.
`status` loads config + root, reads `now` once, and reads each token via
`auth-store/load-tokens` (fresh from auth.json — consistent with isaac-zcsk).

**Acceptance (spec-level, cli_spec.clj — the stated Definition of Done):** all
five states covered as deterministic unit tests on `#'provider-auth-line`
(expired-oauth→EXPIRED, valid-oauth→authenticated+ttl, oauth-no-token→not logged
in, api-key→authenticated (API key), keyless→no auth required), plus two
integration tests via `sut/run ["status"]` asserting the **expired-chatgpt output
is `EXPIRED` and NOT `no auth required`** (the headline bug) and the api-key line.

Note on gherkin: the bean lists both a gherkin and a spec-level acceptance; I used
the spec-level one (its explicit DoD is "the five specs green"), matching the
isaac-zcsk final-acceptance decision that auth is spec-tested by convention here.
Say if you want a gherkin scenario too.

**Verification:** isaac-agent `bb verify` — config-bypass-lint ok; **1172 spec
examples / 2295 assertions, 0 failures**; **578 feature examples / 1295
assertions, 0 failures**; `bb lint` src clean.
