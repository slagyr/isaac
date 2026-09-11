---
# isaac-1yjs
title: 'Turn-based soul placement: keep crew soul out of the cached system block to survive crew swaps'
status: draft
type: task
priority: normal
created_at: 2026-05-25T17:42:32Z
updated_at: 2026-05-25T17:42:38Z
blocked_by:
    - isaac-uysx
---

## Motivation

The crew soul lives in the cached system block. Swapping crew mid-session
(/crew) rewrites the soul → changes the system block → invalidates the cache
from the system block onward (Anthropic caching is prefix-based), including the
whole conversation history. Every crew swap pays a full cache rebuild.

## Idea

Move the soul OUT of the system block into the message stream (a leading,
nonce-tagged trusted message), leaving the system block as just the universal
prompt-injection guard — stable across all crews and sessions → maximal cache
reuse. A crew swap then appends a new soul message instead of rewriting the
system block, localizing the miss to the swap point and preserving the guard
cache + pre-swap history cache.

## Open risks / why deferred

- **Authority.** The soul is the crew's core identity + operating instructions.
  In a non-system message it is less authoritative (instruction hierarchy) and
  more vulnerable to being overridden later in the conversation. Needs eval that
  persona fidelity holds.
- **Payoff depends on frequency.** Only worth it if mid-session crew swaps are
  common. If rare, eating a one-time cache rebuild on swap is cheaper than
  complicating the common path and risking persona authority.
- Depends on the nonce + universal-guard trust mechanism (isaac-uysx): a soul in
  the message stream must be nonce-tagged and trusted via the standing guard,
  exactly like origin metadata.

## Next steps before promoting

- Measure how often crew swaps happen mid-session.
- Prototype soul-as-message; eval persona adherence vs soul-in-system.

## Relationship

- Related to isaac-uysx (shares the nonce / universal-guard trust mechanism).
- Pure caching optimization; no user-facing behavior change.
