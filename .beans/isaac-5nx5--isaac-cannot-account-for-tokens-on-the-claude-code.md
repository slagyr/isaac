---
# isaac-5nx5
title: Isaac cannot account for tokens on the claude-code provider, and a reset session reports a 938k gauge
status: todo
type: bug
priority: high
created_at: 2026-09-24T20:47:50Z
updated_at: 2026-09-24T20:47:50Z
---

Repo: **isaac-agent** (`src/isaac/drive/turn.clj`, session/compaction).

Full measurement and method: `doc/token-burn-isaac-k00m.md`.

## Two problems, found measuring what one bean cost

### 1. A request's composition is invisible

Isaac records per-turn `:usage` in the session transcript, and logs
`:total-tokens` per request for the **chatgpt** provider. For **claude-code**
— the provider every worker crew actually runs on — there is no per-request
accounting at all, and nothing anywhere records what a request was *made of*.

Answering "what did isaac-k00m cost, and why?" took a transcript dig, and still
ended with ~285k tokens per request unaccounted for. That is not a reporting
nicety: it is the difference between "this harness is expensive" and "this
harness has a leak", and today neither can be shown.

### 2. A `:reset` session reports a gauge at 94% of the window

```clojure
:event :session/compaction-skipped
:reason :context-reset
:total-tokens 938870
:context-window 1000000
:session "isaac-work-1"
```

Compaction is skipped *because* the session is `:reset`, on the premise that
reset trims at build time — and it does (`turn.clj:1150` reduces the transcript
to the last user message). So the gauge measures something the request does not.

Measured against that: the isaac-k00m turn made **99 requests averaging ~358k
prompt tokens**. Neither ~72k (fully trimmed) nor ~938k (not trimmed). Several
hundred thousand tokens per request come from somewhere between Isaac's trim
and what the provider receives, and are paid 99 times — roughly 28M of the
turn's 35.5M.

Ruled out: stale transcript (reset does trim), boot files (~9KB), and a local
SDK session store (`~/.tono-claude/sessions/` is empty). Still open: state held
provider-side against the `:session-key` Isaac sends on every request.

## Why it matters

The turn's full-rate spend was ~722k tokens, of which **cache writes were 88%
and output 12%** — the model barely wrote anything. Cost is dominated by
assembling context, which is exactly the part nothing measures.

## Acceptance

- Per-request token accounting exists for the claude-code provider, at parity
  with chatgpt, and is visible without reading a transcript by hand.
- A request's composition is recorded well enough to attribute its tokens —
  at minimum: system/soul, tool schemas, boot files, skill text, transcript.
- The 938k gauge on a `:reset` session is explained: either the gauge is
  corrected to measure what is sent, or the trim is shown not to reach the
  provider and that is fixed. Whichever it is, a scenario pins it.
- A scenario covers a long-lived `:reset` session: the request sent after a
  reset carries the trimmed transcript and not the accumulated one.

## Notes

`isaac-work-1` has accumulated since at least 2026-09-21 and never compacts,
because compaction is skipped for `:reset`. Whether a fresh session per bean
would be cheaper is worth measuring once the accounting above exists — not
before, or it is guesswork again.

## Narrowed by measurement 2026-09-24 — see doc/token-burn-isaac-k00m.md

**The 938k gauge is explained and is NOT a leak.** `run-compaction-check!`
computes it over the full *stored* transcript and then skips compaction because
the session is `:reset`. So it measures something the request never contains,
and on a `:reset` session it will climb toward the window forever while actual
requests stay small. That is still a defect — a log line that reads as "this
session is 94% full" when it is not — but it is a reporting bug, not a leak.
Both build paths trim correctly (`turn.clj:1565` and `:1150`).

**Fixed per-request overhead measured directly: ~32k.** One minimal prompt in a
fresh scrapper session on zanebot:

    :usage {:prompt-tokens 32199, :output-tokens 9,
            :cache-read-tokens 0, :cache-write-tokens 32193}

So soul + boot files + rules + skill menu + tool schemas is 32k. Boot files are
not the problem.

**What remains is one sharp question.** With 32k fixed and a stored turn
transcript of ~87–145k, the expected average request is ~104k and the expected
turn total ~10.3M. Actual: ~358k average, 35.5M total. **A request carried
~3.4× the context Isaac stored for it.**

Two candidates, both testable by logging request size at send time:

1. Tool results are capped when **stored** (`:max-lines`/`:max-bytes` from
   `defaults/tool-caps`) while the model receives them in full. The stored
   transcript would then systematically undercount the sent context, and the
   gap would grow with the number of tool calls — which matches 95 tool
   results.
2. The claude-code provider adds per-request content Isaac never sees.

## Design direction (planner, 2026-09-24)

Where the accounting belongs, having looked at the seams:

- **The provider reports, the drive aggregates.** Only the provider sees the
  wire response, so `:usage` extraction is provider-level — that is why
  chatgpt has it and claude-code does not. But nothing provider-specific should
  decide *what* is recorded: the drive (`isaac.drive.turn`) should demand a
  normalized usage map from every provider and be the single place that
  totals it. A provider that cannot report usage should say so explicitly
  rather than silently contributing nothing.
- **Per-request, not just per-turn.** The per-turn `:usage` already exists in
  the transcript and is the right place for the total. What is missing is the
  per-request line — and it is the per-request number that would have answered
  this in one step instead of a day.
- **Record composition, not just size.** At minimum: system/soul, boot files,
  skill text, tool schemas, transcript. A single "request was 358k" line still
  would not have told us *why*; "transcript 326k of 358k" would have.
- **Log, do not store.** The per-request breakdown belongs in the structured
  log, not the transcript — the transcript is context that gets re-sent, and
  writing accounting into it would make the thing it measures more expensive.
  Per-turn totals stay in the transcript where they already are.

## Correction: the tool-cap hypothesis is dead

The leading candidate above — "tool results are capped when stored but sent in
full" — is **wrong**, checked in code rather than argued. `cap-output` runs
inside `isaac.tool.registry` at execution time:

```clojure
(let [capped (cap-output caps (:result result))]
  (assoc result :result capped))
```

The capped value is what `execute` returns, so the same truncated text becomes
both the transcript entry and the message sent to the model. There is no
divergence at that seam.

So the 3.4× gap between what Isaac stores for a request and what the request
apparently carries is **still unexplained**, with one candidate left standing:
the claude-code provider contributes per-request content Isaac never sees or
records. That cannot be settled by reading Isaac's code — it needs the
request-size logging this bean asks for. Note also that two of Isaac's own
measures of the same turn already disagree by 1.7× (stored bytes ≈ 87k tokens
vs the transcript's own `:tokens` fields summing to 145,260), so the
instrumentation should establish a single trustworthy number before anyone
reasons from the existing ones.

## Also wanted: a usage report back to whoever asked for the turn

The requester should be able to see what a turn cost without reading logs. A
hail-driven turn already reports its outcome (`:hail/turn-ended`); the token
cost belongs in the same place, and a comm-driven turn should be able to
surface it the same way. Concretely:

- the per-turn total travels with the turn's completion, so a hail reply, a
  comm response, or a CLI `isaac prompt` can include it
- it is opt-in per caller, not chattered into every reply — a human asking a
  question over iMessage does not want a token bill appended to the answer
- `isaac prompt` showing it behind a flag is the cheapest useful version and
  probably the place to start
