---
# isaac-4puj
title: Migrate chat to the shared session selector + override
status: draft
type: feature
priority: normal
created_at: 2026-06-26T16:28:54Z
updated_at: 2026-06-26T22:03:46Z
parent: isaac-4e4b
blocked_by:
    - isaac-nbgn
---

Child of isaac-4e4b. Migrate the chat command (isaac-acp chat_cli) onto the shared session selector/resolver/override built in isaac-nbgn (B1). Replace chat's ad-hoc session attach logic with the shared --session/--crew/--session-tag/--spawn/--new/--with-* flags + resolver. chat is interactive, attaches to ONE session (no --reach). Flag contract per isaac-4e4b.

## Acceptance
- chat uses the shared selector + --with-* override; gains --crew/--session-tag selection.
- Interactive attach to the single resolved session; illegal combos error per the shared rules.
- Existing chat behavior preserved.

Blocked by isaac-nbgn (B1). Surfaced 2026-06-26.

## Pending revision (2026-06-26)
Will be revised once the remote-CLI epic (isaac-ec9q: isaac-cli-server + isaac-cli-proxy) lands. With a generic `/cli` channel, the over-the-wire story is handled by remote-cli (server runs the real command), so this bean narrows to "the LOCAL command uses the shared selector" like prompt. Re-scope when ec9q is built.
