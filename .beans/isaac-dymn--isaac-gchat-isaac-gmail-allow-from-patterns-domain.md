---
# isaac-dymn
title: 'isaac-gchat / isaac-gmail: allow-from patterns (*@domain) — Gmail only with an authenticated From'
status: completed
type: feature
priority: high
tags:
    - google
    - comm
    - security
created_at: 2026-09-19T21:13:13Z
updated_at: 2026-09-20T07:07:49Z
parent: isaac-bv1l
---

Micah, 2026-09-19: allow-from is exact-match in both modules; wants `*@tonotop.com`.

Chat: the sender email is Google's own, authenticated — plain pattern match (`*@domain`, exact emails) is safe.

Gmail: the allowlist checks the From: header, which is forgeable. A domain pattern must ALSO require Gmail's authentication verdict for that domain: read the message's `Authentication-Results` header (Gmail adds it) and accept only when dmarc=pass (or spf=pass AND dkim=pass aligned to the From domain). Exact-email entries keep today's behaviour (documented as header-only). Log drops as :sender with a :reason (:pattern-miss / :unauthenticated).

Scenarios (worker writes; inbound features of each module): pattern admits a domain sender (Chat); exact still works; Gmail domain pattern admits a DMARC-pass message and drops a forged From with dmarc=fail; empty allow-from still fails closed.



Scope note (planner 2026-09-20): the DM / discovery / canonical-session-name
paragraphs that had accreted here moved to isaac-xy2i, which is that bean. This
one is allow-from matching only.

## Landed on main (planner, 2026-09-20)

Written and landed by the planner — the zanebot fleet's claude OAuth expired
and every worker turn was coming back empty, so the train ran by hand.

**isaac-gchat** — an entry may be `*@domain`, matched case-insensitively
against the sender's email, including an email the People API resolved from a
users/<id>. Nothing that merely ends with the domain matches
(`eve@nottonotop.com`, `eve@tonotop.com.evil.net` both drop). Exact
addresses, `users/<id>` and `domain:<domainId>` entries are untouched, and an
empty list still fails closed.

**isaac-gmail** — the same pattern, but honoured only when Gmail vouches for
the domain: `dmarc=pass`, or `spf=pass` and `dkim=pass` both aligned to the
From domain, read from Authentication-Results (ARC-Authentication-Results as a
fallback). A pattern-matching sender Gmail will not vouch for drops as
`:unauthenticated` at `:warn`; anything else drops as `:sender` at `:debug`.
Exact addresses stay header-only, as the bean says.

While there: the Gmail gate compared the allow-list against the whole `From:`
header, so `Ada Lovelace <ada@tonotop.com>` would never have matched
`ada@tonotop.com` in production. It now reads the address out of the header.

| repo | suite | result |
| --- | --- | --- |
| isaac-gchat | `bb spec` / `bb features` | 55 / 0, 21 / 0 |
| isaac-gmail | `bb spec` / `bb features` | 33 / 0, 11 / 0 |

main-sha: isaac-gchat 350547c8b50ca91aacda215250869c0870698a8e (0.1.5)
main-sha: isaac-gmail fe740491306d64a92e8cd48e9dc564beeb461d14 (0.1.4)
