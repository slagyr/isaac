---
# isaac-221w
title: estimate-tokens divides chars by 4; measured 2.7 for prose and code, 1.9 for JSON
status: todo
type: bug
priority: high
created_at: 2026-09-24T22:06:14Z
updated_at: 2026-09-24T22:06:14Z
---

Repo: **isaac-agent** (`src/isaac/llm/api/protocol.clj:314`).

## Problem

```clojure
(defn estimate-tokens
  "Estimate token count using content chars/4. Maps are measured from
  message/tool content, never from (str map)."
  [request]
  ... (Math/ceil (/ (double (max 0 chars)) 4.0)))
```

Measured against the real `claude` binary while building isaac-5nx5's
instrumentation:

| content | chars per real token |
|---|---|
| English prose | **2.73** |
| the same prose, doubled | **2.73** |
| real Clojure source | **2.68** |
| JSON-shaped tool-call lines | **1.90** |

So the divisor under-counts by **1.5×–2.1×**, and worst on JSON — which is
what a worker turn's transcript mostly is.

This is no longer a guess. Prose at 25k and 50k chars landed on
`reported = 837 + chars/2.73` to three significant figures, so the ratio is
stable and the estimator is simply calibrated wrong.

## Why it matters

`estimate-tokens` is not decorative. It feeds the context gauge, the per-entry
`:tokens` stamp, `estimate-prompt-tokens` and **compaction planning**. An
estimator that reads 183k of transcript as 87k will decide there is room when
there is not — which is the same direction as every "compaction fired too late"
symptom.

It also made Isaac disagree with itself: isaac-k00m stored 347,927 bytes
(≈87k at chars/4) while its own `:tokens` stamps summed to 145,260. That 1.67×
sits inside the measured band. The stamps were the closer number; the byte
count is the one a day of analysis was built on.

Second, smaller defect, visible in the docstring: content-only counting reads a
**tool schema as literally zero**. isaac-5nx5's `accounting/compose` measures
tools and transcript from their serialized size for exactly this reason.

## Acceptance

- The divisor is calibrated from measurement, not assumption, and the number is
  justified in the docstring with what it was measured against.
- Content shape is accounted for, or the divisor is chosen conservatively
  enough that JSON-heavy transcripts are not under-read — an estimator used for
  compaction should err toward over-counting, since the failure mode of
  under-counting is an overflowed context.
- Tool schemas contribute their real size rather than zero.
- Specs pin the ratios against recorded fixtures so a future change to the
  estimator has to state its evidence.
- Check whether compaction thresholds were tuned against the wrong divisor and
  need moving with it.

## Notes

Found by isaac-5nx5, 2026-09-24. Full measurement table in
`doc/token-burn-isaac-k00m.md`.

## Reframed 2026-09-24: stop estimating what has already been measured

The operator asked why Isaac estimates at all, given the transcript records
token counts. The answer is that **it does not record them** — the stamps are
the same chars/4 guess, computed by a second function:

```clojure
;; isaac/session/store/impl_common.clj:85
(defn- ceil-chars->tokens [chars] (long (Math/ceil (/ (double chars) 4.0))))
(defn- text-tokens [text] (when (string? text) (ceil-chars->tokens (count text))))
```

`stamp-message-tokens` only fills `:tokens` when it is absent, and nothing ever
replaces it with a real figure. So Isaac has two estimators, both dividing by
four, differing only in what content they walk — `message-tokens` includes
tool-call arguments and tool results, `content-chars` does not. **That is the
whole of the 1.67× the analysis tripped over.** Neither number was ever a
measurement.

Correcting the divisor alone would leave that intact: two guesses, better
calibrated, still guesses, still capable of disagreeing.

### The real shape of the fix

An estimate is genuinely needed *before* a request — compaction has to decide
whether to compact before it sends, and the provider's count only arrives with
the reply. So the estimator cannot simply go. But it should shrink to the only
thing it is needed for:

- **What has been sent has been measured.** After isaac-5nx5, every request
  reconciles its estimate against the provider's reported prompt tokens. Once a
  message set has been sent, its real size is known. Store that and stop
  re-guessing it.
- **Only the delta needs estimating** — the pending input and whatever has been
  appended since the last measured request. That is a small number, so being
  wrong about it costs little.
- **Calibrate the remaining estimate from observation**, per provider, rather
  than from a constant. isaac-5nx5's `reconcile` already produces exactly the
  data (`:estimated-tokens`, `:reported-prompt-tokens`, `:ratio`); a running
  ratio per provider beats any divisor chosen by hand, and self-corrects when a
  tokenizer changes underneath us.
- **One estimator, not two.** Whatever survives should be the only place a
  character count becomes a token count.

### Revised acceptance

- The per-entry `:tokens` stamp carries a measured figure once one exists, and
  is distinguishable from an estimate — a consumer can tell which it is holding.
- The context gauge prefers measured sizes and estimates only the unsent
  remainder.
- The surviving estimator is calibrated from `reconcile`'s observed ratios per
  provider, not a hardcoded divisor, and defaults conservatively (over-count) on
  no data — the failure mode of under-counting is an overflowed context.
- `message-tokens` and `content-chars` no longer disagree: one implementation.
- Tool schemas contribute their real size rather than zero.
- Specs pin the behaviour against recorded fixtures, including a case where the
  measured and estimated figures differ and the measured one wins.
- Check whether compaction thresholds were tuned against the wrong divisor.
