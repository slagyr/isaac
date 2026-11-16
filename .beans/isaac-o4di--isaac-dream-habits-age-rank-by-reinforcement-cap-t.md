---
# isaac-o4di
title: 'isaac-dream: habits age — rank by reinforcement, cap the prompt, propose retiring stale or redundant habits, pin vital ones'
status: draft
type: feature
priority: normal
tags:
    - dream
created_at: 2026-11-16T03:10:41Z
updated_at: 2026-11-16T03:10:41Z
parent: isaac-pcm3
---

Repo: **isaac-dream**. Micah, 2026-11-15, after approving Zane's first 38 habits: "over time some habits might become redundant and should be deprioritized or even removed."

## Problem

Active habits only accumulate. Every one goes into the crew's system prompt on every turn (Zane: 38 habits, roughly 3k tokens). Habits go stale (the job moved to another crew), become redundant (a newer active habit says the same thing), or stop coming up. Nothing notices.

## Direction (agreed in chat, 2026-11-15)

- **Rank, then cap.** Order active habits by reinforcement (most recent and most often first), from the `:reinforced` history events that already carry dates and evidence. The habits system section gets a size budget; habits past the budget drop out of the prompt but stay active and stored.
- **Propose retirement, never auto-retire.** A habit with no reinforcement for a configured time (`crew.<id>.dream.stale-after`, e.g. 60 days, defaulted from `defaults.crew.dream`) appears in the dream digest as a retirement proposal. The operator approves (`retire`) or keeps it, which resets its clock. Retired habits keep history and evidence.
- **Merge redundant actives.** When two active habits say the same thing, the dream proposes one habit that `supersedes` both, with the union of their evidence. Approving it retires them (existing supersession rule). Shares prompt work with isaac-gomk (dedupe at proposal time); this bean covers actives already approved.
- **Pin.** `isaac dream pin <id> --crew <id>` marks a habit that never decays and is always in the prompt, for rare-but-vital lessons (never fabricate tool output, privacy). `unpin` reverses it.

## Open questions

- Budget unit: habit count or characters/tokens.
- Does "keep" on a retirement proposal count as a reinforcement event, or a separate `:kept` event?
- Should `dream show` / `dream list` print rank and last-reinforced date.

## Acceptance (draft)

- Habits section orders by reinforcement and honors the budget; pinned habits always appear, first.
- A habit unreinforced past `stale-after` is proposed for retirement in the digest; approving retires it; keeping resets its clock.
- A dream whose window shows two active habits are the same proposes a merged habit superseding both; approving retires both.
- `dream pin`/`unpin` work and pinned habits are never proposed for retirement.
