---
# isaac-xkc9
title: Migrate ACP to the shared session selector + override
status: todo
type: feature
priority: normal
created_at: 2026-06-26T16:28:54Z
updated_at: 2026-06-27T17:42:50Z
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
