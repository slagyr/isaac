---
# isaac-xkc9
title: Migrate ACP to the shared session selector + override
status: completed
type: feature
priority: normal
tags: []
created_at: 2026-06-26T16:28:54Z
updated_at: 2026-06-29T15:18:58Z
parent: isaac-4e4b
blocked_by:
    - isaac-nbgn
    - isaac-rqlc
---

Child of isaac-4e4b. Migrate the ACP command/surface (isaac-acp cli/server) onto the shared session selector/resolver/override from isaac-nbgn (B1). Replace ACP's ad-hoc session attach with the shared --session/--crew/--session-tag/--spawn/--new/--with-* flags + resolver. ACP attaches to ONE session (no --reach). Flag contract per isaac-4e4b.

## Acceptance
- ACP uses the shared selector + --with-* override; gains --crew/--session-tag selection.
- Attach to the single resolved session; illegal combos error per shared rules.
- Existing ACP behavior preserved (stdio + ws transports).

Blocked by isaac-nbgn (B1). Independent of B2 (chat). Surfaced 2026-06-26.

## Pending revision (2026-06-26)
Will be revised once the remote-CLI epic (isaac-ec9q: isaac-cli-server + isaac-cli-proxy) lands. With a generic `/cli` channel, the over-the-wire story is handled by remote-cli (server runs the real command), so this bean narrows to "the LOCAL command uses the shared selector" like prompt. Re-scope when ec9q is built.

## REPURPOSED (2026-06-27): acp accepts the full frequencies CLI args

Narrowed per Micah. ACP-over-the-wire moved to remote-CLI (isaac-ec9q); the proxy-removal is isaac-uek0. This bean is now ONLY: the `acp` command accepts the full frequencies set of CLI args (--session/--crew/--session-tag/--reach?/--prefer/--create/--with-*) by consuming the REUSABLE frequencies-cli adapter (isaac-rqlc), same as prompt. The acp session is then resolved via the shared core. No bespoke acp selection logic.

## Scenarios (2026-06-27) — 2 wiring scenarios; regression net = cli-resume.feature
The acp command consumes the REUSABLE frequencies-cli adapter (isaac-rqlc) to accept --session/--crew/--session-tag/--prefer/--create/--with-*; resolve the acp session via the shared core. Replaces acp/cli.clj's find-most-recent-session + attach-session-handler bypass. Home: features/comm/acp/cli-resume.feature. (Still blocked-by rqlc.)

### S1 — selection wiring: acp --crew resolves via frequencies
crew ketch; sessions ketch-old/ketch-recent (updated-at); stdin initialize + session/new; `acp --crew ketch` -> session/new result.sessionId = ketch-recent (most-recent ketch via shared adapter+resolver).

### S2 — override wiring: acp --with-model
existing session bridge; grover2 -> echo-alt; stdin initialize + session/load bridge + session/prompt; `acp --session bridge --with-model grover2` -> session "bridge" turn runs on echo-alt.

Regression net: cli-resume.feature migrated — acp's --resume -> --prefer; --crew/--session fold into the frequencies adapter. Scope: wiring only (per 4e4b). New steps: none (existing acp cli-resume + agent steps; session/load+session/prompt are JSON-RPC content in the existing stdin step). Pairs with isaac-uek0 (remove the acp --remote proxy).



## Resolution 2026-06-29 — committed isaac-acp 0886361

Two parts; both landed on isaac-acp main.

### Wiring (the bean's actual ask)
- isaac.comm.acp.cli now consumes the reusable frequencies-cli adapter (rqlc),
  same as prompt: option-spec = frequencies-option-spec + override-option-spec
  (--session/--crew/--session-tag/--resume/--prefer/--create + --with-*). Removed
  the bespoke find-most-recent-session / --resume / resolve-attach-key logic.
- run-local resolves the single attach session via
  frequencies/resolve-session-targets; session/new returns the resolved key (or
  the server opens a fresh one per :create). Projects --with-model (model-override)
  and --with-crew (turn crew). Explicit --session still errors "session not found";
  unknown --with-model still errors. Illegal combos validated by the shared rules
  (local only — the --remote proxy forwards flags untouched; proxy removal = uek0).
- features/comm/acp/cli-resume.feature migrated to the new contract:
  * --resume -> most-recent; --crew -> crew-scoped most-recent (S1);
    --crew create-new; --session+--crew rejected (shared rule);
    --with-model grover2 -> echo-alt on the attached turn (S2).
  * cli.feature / proxy.feature crew+session scenarios moved to --with-crew /
    proxy forwarding.

### Prerequisite modernization (acp was ~10 foundation versions behind)
The frequencies adapter only exists at agent 10093b4 (foundation v0.1.12), so acp
had to move from foundation v0.1.2 to the current chain. Fixed the resulting
drift: provider-template list; SessionStore gained get-transcript; should-compact?
is now 3-arg; check-compaction! estimates prompt tokens (tests mock
estimate-prompt-tokens) and the compaction loop recurses-on-progress; prompt/
command discovery moved to prompts/commands (isaac-o8gk).

Pins (after rebasing onto origin which had also bumped agent/server): foundation
v0.1.12 (a834445), agent 10093b4, server eb51cc48.

### Verification (real git pins)
cd isaac-acp && clojure -M:spec -> 199/0 ; -M:features -> 84/0 (10 pre-existing
pending). Regression net cli-resume.feature green (5/0 incl. S1 + S2).

Note: a block of session-resolution tests in spec/isaac/comm/acp/cli_spec.clj
(the "feature harness reproductions" describe) does not execute in the full
suite (a pre-existing loading quirk, not introduced here); the same behavior is
covered by cli-resume.feature which does run. Worth a follow-up to re-enable.

Scope honored: wiring only for the selection/override; no bespoke acp selection
logic. Pairs with isaac-uek0 (remove the --remote proxy). Tagged unverified.


## Verifier follow-up 2026-06-29 — --remote path now honors the contract (isaac-acp d40c22e)

Verifier flagged (correctly) that the proxy path had diverged from the local
contract: remote-query-params forwarded only model/crew/session/resume (dropping
--with-model/--with-crew/--session-tag/--prefer/--create) and validation was
skipped for --remote. Fixed so stdio and --remote agree:
- run validates frequencies options for BOTH transports up front.
- remote-query-params forwards the FULL flag set (selection + all --with-*).
- the websocket SERVER (websocket.clj) resolves via the shared core:
  requested-session-key builds a frequencies map from the query and calls
  resolve-session-targets (no more bespoke session/resume lookup); server-opts
  honors --with-model / --with-crew.
- parse-option-map coerces --create to a keyword.
- proxy.feature: illegal --crew+--session scenario migrated (now rejected),
  --model -> --with-model, and a new --session-tag scenario proves a fresh
  selection flag forwards + resolves remotely.

Re-verified: clojure -M:spec 199/0 ; -M:features 85/0.

## Verification (2026-06-29)
Verified on fetched GitHub `isaac-acp` `main` at `d40c22e68587b2eca95b5a93751dbabba7dbedff`.

Proofs were green:

- `clojure -M:spec` -> `199 examples, 0 failures, 502 assertions, 1 pre-existing pending`
- `clojure -M:features` -> `85 examples, 0 failures, 182 assertions, 10 pre-existing pending`

This includes the remote-path follow-up, so current `main` now honors the shared frequencies contract for both local and `--remote` ACP.
