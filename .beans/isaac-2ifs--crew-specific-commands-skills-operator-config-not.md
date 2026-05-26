---
# isaac-2ifs
title: Crew-specific commands & skills (operator config, not quarters)
status: draft
type: feature
priority: normal
created_at: 2026-05-26T04:21:33Z
updated_at: 2026-05-26T04:21:33Z
parent: isaac-nwj3
blocked_by:
    - isaac-8qd5
---

Deferred from isaac-nwj3 (agreed to wait). Crew-specific commands/skills as a third layer.

MUST be **operator-authored config** (e.g. `~/.isaac/config/crew/<id>/{commands,skills}/`), **never crew-writable quarters** — quarters are crew-writable, so crew-authored prompts/skills = self-modification / privilege escalation, forbidden by the security posture (same trust boundary as the soul).

Open: precedence vs project (lean install < crew < project for commands; skills additive).

Parent: isaac-nwj3.
