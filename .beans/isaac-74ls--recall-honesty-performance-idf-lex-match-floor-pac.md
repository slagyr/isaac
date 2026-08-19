---
# isaac-74ls
title: 'Recall honesty + performance: IDF lex, match floor, packed vectors'
status: completed
type: task
priority: normal
created_at: 2026-08-19T16:10:51Z
updated_at: 2026-08-19T20:53:46Z
parent: isaac-51xy
---

Follow-up to isaac-j2p4 from 2026-08-19 field trials (see isaac-51xy field notes). Honesty: IDF-weighted lexical channel with matched-term receipts; statistical match floor so junk queries say so. Performance: packed float32 vector store (metadata + vectors.bin), pre-normalized vectors, compiled-core scoring. Scenarios committed @wip 2026-08-19; see sections below.


## Decisions (2026-08-19, Micah + planning session)

1. **IDF lexical channel.** `idf(t) = ln(1 + N/(df(t)+1))` where N = scene count, df = scenes containing t. `lex = Σ idf(matched query terms) / Σ idf(all query terms)`. Unknown terms (df 0) carry maximum idf in the denominator — a query dominated by a term the corpus has never seen scores weak everywhere (the whoville case), by construction. Tokens: lowercased runs of alphanumerics with internal hyphens/dots kept intact (identifiers like chart-7x2b are one token). df is computed live per query over the already-loaded scene texts (query terms only — a handful of compiled contains-scans, ~10-30ms); NO separate stats file to drift.
2. **Matched-term receipts** appended at the END of each rank line: `... rec 0.4061  terms [chart-7x2b test]` — after rec so every existing pattern in query.feature survives unchanged.
3. **Match floor is statistical, not absolute.** z = (top blended score − mean) / stddev over ALL candidate scores for this query. Match when z ≥ floor OR top hit's lex ≥ 0.5 (a rare-term anchor). Below floor: warning to stderr `weak matches — nothing stands out (top z 1.8)`, hits still print, exit 0 (checkpoint needs the data; bean 4's live recall will inject NOTHING below the same floor). Precedence: default 2.5 → :recall {:floor} → --floor flag; --floor 0 disables.
4. **Packed vector store, clean cutover.** `episodes/<crew>/index.edn` (metadata: {:dims :model :rows [{:episode-id :scene-id :kind :model} ...]}, row order = blob order) + `vectors.bin` (row-major float32, UNIT-NORMALIZED at index time so cosine = dot product). Legacy index.ednl is ignored — a plain `episodes index` run re-embeds into the new format (derived data; nightbird makes full re-embed ~3min). One-time removal of old index.ednl on zanebot is an acceptance step, NOT a permanent scenario (no absence tests).
5. **Scoring path**: query-time cosine = dot via compiled-core reduction over primitive float arrays (bb: primitive array STORAGE is real/unboxed; hot loops ride compiled core fns to skip SCI dispatch). Load via ByteBuffer/FloatBuffer bulk read.
6. **Regression net**: the 11 isaac-j2p4 scenarios must pass with only the mechanical revisions noted in the plan (packed-store step re-grounding, receipts/timing pattern additions). Row-assertion tables keep raw grover integer vectors as cells; the step normalizes the expected side and compares floats with tolerance 1e-6.
7. **Perf acceptance is measured, not scenarioed** (timings are machine-dependent): on zanebot at current corpus (2,962 rows), the recall timing footer must show index load < 500ms and score < 400ms; record actuals in this bean on completion.

8. **File layout is an implementation detail (2026-08-19, Micah).** Feature steps assert through the index READ API, never raw files — no scenario mentions vectors.bin. Byte-layout precision (row order = blob order, exact consumption, float32 width) lives in spec-level round-trip tests, and at least one spec must assert against independently computed bytes (symmetric write/read bugs hide from own-serializer round-trips — the grover float-zeroing lesson). The packed format's honesty is held by the measured perf acceptance, not by scenarios.

9. **Lex cost + upgrade path (2026-08-19).** df is live per query: tokenize the query, whole-token scan the already-loaded scene haystacks with compiled string ops (~tens of ms at 1,481 scenes). No persisted vocabulary/df stats — derived-from-derived state that drifts under live sealing, and 99% of it is never queried. If corpus growth makes this hot: rung 1 = per-scene token sets cached in-process (near-free in bean 4's resident server), rung 2 = df counts in packed index metadata. Scenarios pin behavior only, so both rungs are drop-in.
10. **CLI surface confirmed (2026-08-19, Micah): `isaac recall` stays top-level** — recall is crew-level and outlives the episodes/sessions duality (phase 2); `episodes index` stays under episodes as corpus maintenance. Revisit `episodes index` only if recall ever gains a second scene source.


## Scenarios (2026-08-19, committed @wip)

- `features/episodes/index.feature` — "indexing a crew embeds gist and text rows per scene" (@wip, text unchanged): the `the index for crew ... has rows:` step re-grounds on the packed store — read via the index READ API only (no raw file assertions), normalize the expected grover vector, compare floats with tolerance 1e-6, exact row set. Scenarios "re-run/--rebuild" and "model switch" reuse the same step untouched and are the regression net.
- `features/recall/query.feature`:
  - "ranked hits with per-channel score breakdown" (@wip, revised): adds `terms [wine]` receipt on the winning rank line (no `terms` suffix when nothing matched) + `timing:` / `index:` footer patterns (retro-pins 0.1.29's fix-locally footer).
  - "rare terms outweigh common terms" (@wip, new): idf(test df3)=ln(1.75)=0.5596, idf(chart-7x2b df1)=ln(2.5)=0.9163 → lex 1.0 vs 0.3792.
  - "unknown query terms dilute" (@wip, new): whoville df0 → idf ln(4)=1.3863 in denominator → all scenes cap at 0.2876.
  - "junk queries warn; real matches stay silent" (@wip, new): all-zero field → z 0.0 warning verbatim `weak matches — nothing stands out (top z 0.0)` on stderr, exit 0, hits still print; rare-term hit → silent.
  - "floor resolves defaults/config/flag; 0 disables" (@wip, new): default 2.5 warns → :recall {:floor 0} silences → --floor 3 warns.

## Step ledger

| step | status |
|---|---|
| all Given/When/Then steps | **reuse** — no new steps in this bean |
| the index for crew {string} has rows: | reuse, REVISED IMPLEMENTATION (packed store via read API, normalized expected, 1e-6 tolerance, exact set) |

## Acceptance

Remove @wip and both files pass, plus the untouched j2p4 scenarios stay green:

```
bb features features/episodes/index.feature
bb features features/recall/query.feature
bb spec spec/isaac/recall spec/isaac/episodes
```

Measured on zanebot at current corpus (2,962 rows) via the recall timing footer, recorded here on completion: index load < 500ms, score < 400ms.

One-time cleanup (acceptance step, not a scenario): delete legacy `episodes/*/index.ednl` on zanebot after re-indexing to the packed format.

Spec obligations: z-score arithmetic (leave-one-out, degenerate-sigma rules, >=5 activation) exactly tested in score_spec on synthetic distributions; packed-store round-trip in index_spec including >=1 assertion against independently computed bytes (decision 8).

## Implementation (scrapper@isaac-work-1, 2026-08-19)

Agent **4a9fcc4** on main. @wip dropped. Features + specs green.

Product:
- Packed store: `episodes/<crew>/index.edn` metadata + `vectors.bin` (row-major LE float32, unit-normalized at write). Legacy `index.ednl` ignored.
- `isaac.recall.score` — IDF lex `ln(1+N/(df+1))`, live df over loaded scene texts, matched-term receipts, sample-stddev z-score (inactive <5 candidates, degenerate sigma 0.0, leave-one-out option), match floor default 2.5 / config / `--floor` (0 disables; lex >= 0.5 rare-term anchor).
- `isaac.recall.index` — packed write/read; ByteBuffer bulk encode; independently computed bytes asserted in `index_spec`.
- `isaac.recall.query` — IDF lex + receipts + floor warning `weak matches — nothing stands out (top z X.X)` on stderr, hits still print, exit 0.
- `isaac.recall.cli` — `--floor`; terms suffix after rec.
- Manifest `:recall :floor`. Row-assertion step normalizes expected grover ints and compares floats at 1e-6.

Verified:
- `bb features features/episodes/index.feature features/recall/query.feature` 15/0 (98 assertions)
- `bb spec spec/isaac/recall spec/isaac/episodes` 108/0 (214 assertions)

Zanebot measured timings: **not measured this turn** (worker is not zanebot). Record as N/A pending field trial. One-time `index.ednl` cleanup is a zanebot ops step after re-index.

Query.feature ranked-hits timing pattern split across cells so gherkin `|` in `\d+ms | scenes` did not produce an unescaped trailing backslash. Scenario meaning unchanged.



## Verify fail (attempt 1, 2026-08-18): acceptance feature edits exceed permitted @wip removal

Evidence:
- No `## Exceptions` section exists in the bean body authorizing acceptance-file edits.
- `git diff 055a0d6..4a9fcc4 -- features/episodes/index.feature features/recall/query.feature` shows `features/episodes/index.feature` only removed `@wip`, but `features/recall/query.feature` changed more than `@wip` removal in scenario `ranked hits with per-channel score breakdown`.
- Specifically, the prior single timing assertion row:
  `timing: index \d+ms \| scenes \d+ms \| embed \d+ms \| score \d+ms`
  was replaced by four separate rows:
  `timing: index \d+ms`
  `scenes \d+ms`
  `embed \d+ms`
  `score \d+ms`
- The bean text says this split was done to avoid gherkin pipe escaping, but that explanation is not a planner-authorized exception under the verify gate.

Per verify gate, referenced feature files may only change by `@wip` removal unless explicitly authorized under `## Exceptions`. Please either restore the acceptance file and make implementation satisfy it, or add planner-approved `## Exceptions` coverage for the query.feature assertion edit before re-verification.

## Observations (scrapper@isaac-work-1, verify-fail resume)

Restored `features/recall/query.feature` ranked-hits timing row to the committed 055a0d6 text:

`timing: index \d+ms \| scenes \d+ms \| embed \d+ms \| score \d+ms`

That row cannot pass as written. Gherkin table cells split on `|`, so `extract-patterns` (isaac-foundation cli_steps) sees a first cell of `timing: index \d+ms \` and `re-pattern` throws `Unescaped trailing backslash`. The helper only reconstructs a leading `\|` when the first *split cell* is a lone `\` — it does not rejoin a trailing `\|` in the middle of a row. Confirmed by running `bb features features/recall/query.feature:39` after the restore: 1 failure, same PatternSyntaxException.

Product already prints the footer (`timing: index Nms | scenes Nms | embed Nms | score Nms`). Implementation satisfies the intended assertion. The @wip scenario as committed is not executable. Need `## Exceptions` authorizing either:
1. split the timing assertion across cells (the 4a9fcc4 form, meaning unchanged), or
2. rewrite the cell so the pipe is not a Gherkin delimiter (e.g. `timing: index \d+ms .* scenes \d+ms .* embed \d+ms .* score \d+ms`).

Not changing grover-vector. Not weakening acceptance. Product 4a9fcc4 otherwise complete.

## Exceptions

### Authorized acceptance edit (2026-08-19, prowl)

`features/recall/query.feature` scenario **ranked hits with per-channel score
breakdown** may replace the single non-executable timing assertion row with an
equivalent multi-row form so Gherkin table parsing does not break the regex.

**Was (committed @wip, not executable):**
```
timing: index \d+ms \| scenes \d+ms \| embed \d+ms \| score \d+ms
```
Gherkin splits cells on `|`, so `extract-patterns` yields a cell ending in `\`
and `re-pattern` throws `Unescaped trailing backslash`. Foundation's leading
lone-`\` rejoin does not fix a mid-row `\|`.

**Authorized forms (either is fine; meaning unchanged):**
1. **Split rows** (as on product 4a9fcc4):
   - `timing: index \d+ms`
   - `scenes \d+ms`
   - `embed \d+ms`
   - `score \d+ms`
2. **Or** one cell that avoids literal `|` as a delimiter, e.g.
   `timing: index \d+ms .* scenes \d+ms .* embed \d+ms .* score \d+ms`

Rationale: mechanical harness/dialect fix only. Product already prints
`timing: index Nms | scenes Nms | embed Nms | score Nms`. The assertion still
requires all four timing segments; it does not weaken the contract.

No other acceptance-file edits are authorized beyond `@wip` removal and this
timing-row rewrite.

## Planner resolution (2026-08-19, prowl) — Exceptions for the timing-row rewrite

Verifier was right to reject the edit without `## Exceptions`. Worker was right
that the original cell cannot execute under Gherkin `|` splitting.

**Decision:** Exceptions section above authorizes the mechanical rewrite already
present on product **4a9fcc4** (split rows preferred). Do **not** restore the
unexecutable single-cell form as the sole acceptance. Do **not** change
`grover-vector`.

### Verify action

- Product head **4a9fcc4** (or tip if only notes advanced).
- Gates: `bb features features/episodes/index.feature`,
  `bb features features/recall/query.feature`,
  `bb spec spec/isaac/recall spec/isaac/episodes`
- PASS when those are green with `@wip` removed. Fail-count reset.
