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
updated_at: 2026-09-20T00:06:00Z
parent: isaac-bv1l
---

Micah, 2026-09-19: allow-from is exact-match in both modules; wants `*@tonotop.com`.

Chat: the sender email is Google's own, authenticated — plain pattern match (`*@domain`, exact emails) is safe.

Gmail: the allowlist checks the From: header, which is forgeable. A domain pattern must ALSO require Gmail's authentication verdict for that domain: read the message's `Authentication-Results` header (Gmail adds it) and accept only when dmarc=pass (or spf=pass AND dkim=pass aligned to the From domain). Exact-email entries keep today's behaviour (documented as header-only). Log drops as :sender with a :reason (:pattern-miss / :unauthenticated).

Scenarios (worker writes; inbound features of each module): pattern admits a domain sender (Chat); exact still works; Gmail domain pattern admits a DMARC-pass message and drops a forged From with dmarc=fail; empty allow-from still fails closed.



Bumped 2026-09-19 (Micah): DMs are a space too and are not subscribed unless listed, so today a DM to yopp@ is never heard. Discovery (spaces.list, DMs included) is what makes DMs just work.



Micah 2026-09-19: the default is 'a space is a conversation and a conversation is a session' — every space Yopp is a member of (DMs included) routes to a canonical session without any config; entries only override. Canonical session NAME should be readable: the space displayName for named spaces (gchat/yopp-test), the other member's displayName for a DM (gchat/dm/micah-martin), with the space id carried as a session tag (space:AAQA7rg5Uyc) so a rename never orphans the session. spaces.get / spaces.members give the names.
