---
# isaac-4puj
title: Migrate chat to the shared session selector + override
status: draft
type: feature
priority: normal
created_at: 2026-06-26T16:28:54Z
updated_at: 2026-06-27T17:57:54Z
parent: isaac-4e4b
blocked_by:
    - isaac-nbgn
    - isaac-ec9q
---

Child of isaac-4e4b. Migrate the chat command (isaac-acp chat_cli) onto the shared session selector/resolver/override built in isaac-nbgn (B1). Replace chat's ad-hoc session attach logic with the shared --session/--crew/--session-tag/--spawn/--new/--with-* flags + resolver. chat is interactive, attaches to ONE session (no --reach). Flag contract per isaac-4e4b.

## Acceptance
- chat uses the shared selector + --with-* override; gains --crew/--session-tag selection.
- Interactive attach to the single resolved session; illegal combos error per the shared rules.
- Existing chat behavior preserved.

Blocked by isaac-nbgn (B1). Surfaced 2026-06-26.

## Pending revision (2026-06-26)
Will be revised once the remote-CLI epic (isaac-ec9q: isaac-cli-server + isaac-cli-proxy) lands. With a generic `/cli` channel, the over-the-wire story is handled by remote-cli (server runs the real command), so this bean narrows to "the LOCAL command uses the shared selector" like prompt. Re-scope when ec9q is built.

## RESCOPE + HOLD (2026-06-27)
chat does NOT resolve sessions — it's a thin launcher that spawns local Toad pointed at an acp agent, forwarding flags to the agent command. build-toad-command already takes {crew model remote resume session token}, i.e. it already has a local/remote split (today: `isaac acp [--remote URL --token T] [flags]`).

Two coupled changes, NOT just frequencies forwarding:
1. Forward the full frequencies flag set (--crew/--session-tag/--prefer/--create/--with-*) to the acp agent command; renames --resume->--prefer, --model->--with-model.
2. Migrate the REMOTE path: the bespoke `isaac acp --remote URL` goes away with isaac-uek0/isaac-ec9q; remote chat becomes `isaac remote <url>/cli acp <flags>`. Local stays `isaac acp <flags>`. chat picks local vs remote and builds the right command. The existing --remote/--token handling moves to the remote-cli model (parts may become unneeded).

HELD in draft: depends on ec9q's remote-acp path (isaac remote .../cli acp) being concrete + uek0 removing acp --remote. Revisit scope (and whether chat slims down) once those land. Then drive scenarios: ~1 local-forward + ~1 remote-command-construction.
