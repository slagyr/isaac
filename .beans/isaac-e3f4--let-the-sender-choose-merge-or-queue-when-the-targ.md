---
# isaac-e3f4
title: Let the sender choose merge or queue when the target session is busy
status: draft
type: feature
priority: normal
created_at: 2026-10-02T14:51:55Z
updated_at: 2026-10-02T14:51:55Z
---

Idea (Micah, 2026-10-02), from isaac-r209. Whether a request that reaches a busy session MERGES into the running turn (today's busy-session coalescing, right for a burst of chat messages from one person) or QUEUES as its own turn (right for distinct work requests like hails) should be the sender's choice, carried on the charge, likely as a frequencies field, e.g. `:on-busy :merge | :queue`.

Defaults to settle: comms (chat bursts) → `:merge`; hails → `:queue`, with a hail able to ask for `:merge` when the sender wants it folded in (e.g. a follow-up correction to work in progress); CLI `prompt --queue` → `:queue`.

Not started; design questions: where the field lives (frequencies vs the charge itself), how it interacts with isaac-r209's idle-session preference, and how the agent handbook's Frequencies section documents it.
