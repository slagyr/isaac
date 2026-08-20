---
# isaac-l1kz
title: Cosine match floor + recency default 0.5 (checkpoint verdicts)
status: in-progress
type: task
priority: normal
created_at: 2026-08-20T20:40:32Z
updated_at: 2026-08-20T21:10:45Z
parent: isaac-51xy
---

Implements CHECKPOINT verdicts 1-2 from isaac-51xy (2026-08-20). Floor: match iff max(text,gist) cosine >= :recall {:floor-cos} (default 0.47) OR lex >= 0.5; z-score floor retired (EVT-blind at corpus scale, cos floor covers small-n too). Warning becomes 'weak matches — nothing stands out (best cos 0.41)'. Flag renamed --floor-cos; 0 disables; precedence defaults -> config -> flag unchanged. Recency default weight 0.5 parts (was 1), half-life 30d unchanged. Clean cutover: --floor flag and z machinery removed, the two isaac-74ls floor scenarios rewritten (grover fixtures use --floor-cos 0.999 overrides since grover cosines live at 0.9+). Spec obligations: default-weights map asserts recency 0.5; floor evaluation on raw cosines independent of channel weights.


## Scenarios (2026-08-20, committed @wip)

Both are in-place revisions in `features/recall/query.feature` (same titles as the isaac-74ls z versions):
- "junk queries warn that nothing stands out; real matches stay silent" — cosine form. Three runs: no-lex query below --floor-cos 0.999 warns with `best cos` in the message; verbatim-scene-text query (cos exactly 1.0) silent via the gate; rare-term query silent via the lex anchor EVEN WITH --w-lex 0 (floor and anchor read raw channel values, independent of weights).
- "floor resolves defaults, then :recall config, then CLI flag; 0 disables" — first run SILENT under the shipped 0.47 default (grover cosines ~0.9x pass), config {:floor-cos 0.999} warns, --floor-cos 0 silences. Two-scene fixture doubles as proof the z-era >=5 activation rule is gone (cos floor is n-independent).

## Step ledger

| step | status |
|---|---|
| all steps | **reuse** — no new steps, no revised implementations |

## Production notes

- score.clj: default-weights :recency 1.0 -> 0.5; z-score/match? machinery replaced by {:best-cos vs floor-cos} OR {lex >= 0.5}; resolve-floor re-keyed to :recall {:floor-cos} default 0.47.
- cli.clj: --floor flag removed (clean cutover), --floor-cos added; help text updated (help scenario patterns use \s+ loose matches — verify they still pass).
- query.clj: best-cos = max over candidates of max(text,gist); warning message `weak matches — nothing stands out (best cos %.2f)`.
- Spec obligations: default-weights map asserts recency 0.5; floor evaluated on raw cosines/lex regardless of weight flags; z fns deleted with their specs (no absence tests).

## Acceptance

Remove @wip; both revised scenarios pass; all previously-green suites stay green:

```
bb features features/recall/query.feature
bb features features/episodes/index.feature features/episodes/migrate_session.feature
bb spec spec/isaac/recall spec/isaac/episodes
```

Post-deploy field check (recorded here): on zanebot, "marketing page" (scrapper) warns with best cos ~0.43-0.46; "grok oauth refresh token fix" stays silent; R12/R13 exploratory queries still warn.
