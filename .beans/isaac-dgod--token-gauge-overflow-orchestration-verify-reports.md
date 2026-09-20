---
# isaac-dgod
title: 'Token gauge overflow: orchestration-verify reports 12.0M / 278K (4320%) after one compaction'
status: todo
type: bug
priority: normal
created_at: 2026-09-03T00:00:08Z
updated_at: 2026-09-20T20:32:46Z
---

Observed 2026-09-02 on zanebot: `isaac sessions list` shows orchestration-verify (perceptor, gpt-5.4 chatgpt, 327 turns, 1 compaction) at Context 12,031,158 / 278,528 = 4320%. The session file is 1.0M on disk (~250K tokens plausible), so the gauge is not a real prompt size — last-input-tokens (or whatever feeds the PCT column) has gone cumulative or been fed a non-prompt number. Related: isaac-pqjn / isaac-x2up token accounting. Questions: (1) which provider response field seeded 12M — chatgpt usage totals across a stateful chain? (2) does compaction run against this gauge (it would plan chunks off a fictional size) or refuse? (3) is any other session drifting the same way (all other rows look sane today). Reproduce by inspecting orchestration-verify/current.ednl last-input-tokens entries on zanebot before touching the session.

## Mirrored finding from isaac-vuto (2026-09-03)

Accepted finding from `isaac-vuto`: the 12,031,158 stamp on `orchestration-verify` was provider-reported, not sidecar accumulation.

Evidence recorded there:
- `session.edn` contains `:last-input-tokens 12031158`
- `current.ednl` final assistant message contains usage with:
  - `:input-tokens 12031158`
  - `:output-tokens 19146`
  - `:cache-read 11174912`
- therefore the pre-fix turn-end path persisted a provider-reported prompt value verbatim; decision 2's "last, never sum" guard did not create this number
- accepted disposition: cap implausible provider-reported stamps at `context-window` and log `:session/stamp-implausible`


## Disposition superseded (2026-09-20)

The accepted disposition above — clamp implausible stamps at `context-window` and
log `:session/stamp-implausible` — **shipped**, as isaac-agent `4b7c8ac`
("fix token gauge mid-turn stamping"), `normalized-provider-prompt-tokens` at
`turn.clj:225`. It bounds the displayed number and logs a warn. The defect it was
meant to expose is unchanged, and the warn is now routine noise rather than an alarm.

## Second provider, same defect (2026-09-20, claude-opus-5)

`isaac-verify` on zanebot, claude-code provider:

- 27 x `:session/stamp-implausible :prompt-tokens 802832 :context-window 200000`
- transcript at that moment: 68 entries, 144,628 bytes (~36K tokens plausible)
- same turn: `:session/message-stored {:tokens {:input-tokens 3374284 :output-tokens 31056}}`

The 3,374,284 is `reduce add-usage` over `:cycle-usages` in the claude driver — a
correct **turn total**. The 802,832 is the CLI's aggregate usage for the turn.
Neither is a current prompt size.

Compare the original chatgpt case in this bean: `:input-tokens 12031158` with
`:cache-read 11174912`, accumulated across a stateful `:response-id` chain.

Two providers, two accumulation mechanisms, one misreading.

## Root cause

`stamp-provider-prompt!` (isaac-agent `turn.clj:254`) reads a response's
`:prompt-tokens` as "how full is the window now". That holds only for a stateless,
per-request API. It is false for:

- **claude-code** — the CLI reports aggregate usage for the whole turn
- **chatgpt stateful** — usage accumulates across the `:response-id` chain
  (`provider-stateful?`, `turn.clj:240`)

The gauge is not miscalibrated; it is reading a *total* where it needs an
*instantaneous* value. Clamping a total to the window produces a number that is
wrong but plausible-looking, which is worse than one that is obviously wrong —
that is how work-2 and work-3 reached 295% and 253% while still being dispatched to.

## Decision (2026-09-20): adapters report prompt size explicitly

Each llm-api adapter extracts a real current-prompt figure — not a turn total, not
cache-inclusive — and declares when it cannot.

- add an explicit prompt-size field to the adapter contract, distinct from turn usage
- an adapter that cannot supply one says so; the gauge renders **unknown** rather
  than a fabricated number, and writes nothing
- the clamp stays as a backstop, not as the answer
- `:session/stamp-implausible` becomes a real alarm meaning "an adapter is wrong",
  not routine noise

## Acceptance

- a claude-code turn stamps a prompt size that tracks the transcript's actual size
  and is `<=` context-window, without relying on the clamp
- a chatgpt stateful chain of M turns does not accumulate: the stamp after turn M
  reflects turn M's prompt, not the chain total
- an adapter that cannot report prompt size leaves the stamp untouched and the
  gauge renders unknown — no zero is written (see isaac-166j, where a failed
  request's usage zeros reset the gauge)
- `:session/stamp-implausible` does not fire in normal operation on any configured
  provider

## Not in scope

The claude-cli **replay burst** — N identical stamps and N full transcript reads
per turn — is isaac-8cur. That bean owns the frequency; this one owns the value.
Split because they live in different modules with different baselines
(isaac-claude-code vs isaac-agent).
