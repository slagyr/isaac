---
# isaac-xkc9
title: Migrate ACP to the shared session selector + override
status: draft
type: feature
priority: normal
created_at: 2026-06-26T16:28:54Z
updated_at: 2026-06-26T16:29:07Z
parent: isaac-4e4b
blocked_by:
    - isaac-nbgn
---

Child of isaac-4e4b. Migrate the ACP command/surface (isaac-acp cli/server) onto the shared session selector/resolver/override from isaac-nbgn (B1). Replace ACP's ad-hoc session attach with the shared --session/--crew/--session-tag/--spawn/--new/--with-* flags + resolver. ACP attaches to ONE session (no --reach). Flag contract per isaac-4e4b.

## Acceptance
- ACP uses the shared selector + --with-* override; gains --crew/--session-tag selection.
- Attach to the single resolved session; illegal combos error per shared rules.
- Existing ACP behavior preserved (stdio + ws transports).

Blocked by isaac-nbgn (B1). Independent of B2 (chat). Surfaced 2026-06-26.
