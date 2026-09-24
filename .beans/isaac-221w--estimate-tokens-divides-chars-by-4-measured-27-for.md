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
