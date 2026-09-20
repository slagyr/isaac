---
# isaac-dymn
title: 'isaac-gchat / isaac-gmail: allow-from patterns (*@domain) — Gmail only with an authenticated From'
status: todo
type: feature
priority: high
tags:
    - google
    - comm
    - security
created_at: 2026-09-19T21:13:13Z
updated_at: 2026-09-20T07:02:56Z
parent: isaac-bv1l
---

Micah, 2026-09-19: allow-from is exact-match in both modules; wants `*@tonotop.com`.

Chat: the sender email is Google's own, authenticated — plain pattern match (`*@domain`, exact emails) is safe.

Gmail: the allowlist checks the From: header, which is forgeable. A domain pattern must ALSO require Gmail's authentication verdict for that domain: read the message's `Authentication-Results` header (Gmail adds it) and accept only when dmarc=pass (or spf=pass AND dkim=pass aligned to the From domain). Exact-email entries keep today's behaviour (documented as header-only). Log drops as :sender with a :reason (:pattern-miss / :unauthenticated).

Scenarios (worker writes; inbound features of each module): pattern admits a domain sender (Chat); exact still works; Gmail domain pattern admits a DMARC-pass message and drops a forged From with dmarc=fail; empty allow-from still fails closed.



Scope note (planner 2026-09-20): the DM / discovery / canonical-session-name
paragraphs that had accreted here moved to isaac-xy2i, which is that bean. This
one is allow-from matching only.
