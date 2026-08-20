---
# isaac-l1kz
title: Cosine match floor + recency default 0.5 (checkpoint verdicts)
status: completed
type: task
priority: normal
created_at: 2026-08-20T20:40:32Z
updated_at: 2026-08-20T22:03:15Z
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

## Implementation notes (scrapper@isaac-work-1, 2026-08-20)

Product landed locally (not tagged unverified — one approved scenario still @wip). Grover-vector unchanged.

- score.clj: default-weights :recency 0.5; default-floor 0.47; z-score/mean/stddev deleted; match? is best-cos ≥ floor-cos OR lex ≥ 0.5; resolve-floor reads :recall {:floor-cos} then CLI :floor-cos.
- cli.clj: --floor removed, --floor-cos added; help updated.
- query.clj: best-cos = max over candidates of max(text,gist); warning `weak matches — nothing stands out (best cos %.2f)`.
- manifest :recall :floor → :floor-cos.
- Specs: recency 0.5; cosine floor; z specs deleted. query_spec junk warning uses best-cos form.

Verified:
- bb spec spec/isaac/recall spec/isaac/episodes — 115/0 (240)
- floor-resolves scenario, index.feature, migrate_session.feature green
- remainder of query.feature green except the junk-warn scenario

## Conflict (returning to planner)

Approved scenario "junk queries warn that nothing stands out; real matches stay silent" asserts:

```
When isaac is run with "recall lighthouse --crew cordelia --floor-cos 0.999 --w-lex 0"
Then ... stdout rank 1 is 2026-03-01-1008-s5x5 with terms [lighthouse]
```

That rank-1 assertion is a leftover of the isaac-74ls z-era run (`--w-text 0 --w-gist 0 --w-recency 0`), where lex was the only ranking channel. With cosine form + `--w-lex 0`, ranking is cosine+recency. Grover 4-dim char-stat vectors put "Wine pairing" / "a light pinot noir for the pheasant" above the lighthouse scene for query "lighthouse" (best-cos ~0.9997 vs ~0.996). Lex-anchor still silences the warning (the actual floor contract). Changing grover-vector is out of scope.

Need planner to drop/rephrase the rank-1 stdout row (keep warning-silence + terms [lighthouse] anywhere) or otherwise unblock. Floor-resolves scenario is green and @wip-free.

## Exceptions

### Authorized acceptance edit (2026-08-20, prowl)

In `features/recall/query.feature` scenario
**junk queries warn that nothing stands out; real matches stay silent**, the
third run (`recall lighthouse --crew cordelia --floor-cos 0.999 --w-lex 0`)
may drop or rephrase the rank-1 stdout assertion that requires scene
`2026-03-01-1008-s5x5` in position 1 with `terms [lighthouse]`.

**Was (z-era leftover, wrong under cosine ranking):**
```
Then the stdout matches: (rank-1 is 2026-03-01-1008-s5x5 … terms [lighthouse])
```
That assertion assumed pure-lex ranking (`--w-text 0 --w-gist 0 --w-recency 0`).
With cosine form + `--w-lex 0`, ranking is cosine+recency; grover char-stat
vectors put "Wine pairing" / pinot above the lighthouse scene for query
"lighthouse". That is expected cosine behavior, not a product bug.

**Required contract for that run (keep):**
1. **No weak-match warning** on stderr (lex-anchor silences the floor even
   with `--w-lex 0` — floor/anchor read raw channel values).
2. **`terms [lighthouse]` appears somewhere** in stdout (receipt still names
   the matched rare term), without requiring a specific rank position for the
   lighthouse scene.

Optional: assert lighthouse scene id appears somewhere in the ranked hits, or
assert exit 0 — still no rank-1 requirement.

Rationale: this bean's floor contract is cosine floor + lex anchor, not
lex-only ranking. Pinning z-era rank order fights grover cosine and is out of
scope. Do **not** change `grover-vector`.

No other acceptance-file edits beyond `@wip` removal and this junk-warn
rephrase.

## Planner resolution (2026-08-20, prowl) — drop rank-1; keep silence + terms

Worker diagnosis accepted. Floor-resolves scenario already green.

**Decision:** Exceptions above authorize dropping/rephrasing the rank-1 row.
Product **fe9537a** (or tip) stands for cosine floor + recency 0.5.

### Work action

1. Edit the junk-warn scenario third run: remove rank-1 lighthouse requirement;
   keep warning-silence + `terms [lighthouse]` anywhere (and exit 0 if useful).
2. Remove `@wip` from that scenario.
3. Green gates:
   ```
   bb features features/recall/query.feature
   bb features features/episodes/index.feature features/episodes/migrate_session.feature
   bb spec spec/isaac/recall spec/isaac/episodes
   ```
4. Hand to verify. Fail-count reset.

## Implementation (scrapper@isaac-work-1, 2026-08-20)

Planner Exceptions applied. Agent **5a83ce6** (on **fe9537a** cosine floor). Grover-vector unchanged.

Junk-warn third run: dropped rank-1 `1. 2026-03-01-1008-s5x5`; kept no weak-match warning, `terms [lighthouse]` anywhere, lighthouse scene id anywhere, exit 0. @wip removed.

Verified:
- `bb spec spec/isaac/recall spec/isaac/episodes`: 115 examples, 0 failures, 240 assertions
- `bb features query/index/migrate_session`: 30 examples, 0 failures, 207 assertions

Zanebot post-deploy field check not run from this checkout.

## Field check (2026-08-20, 0.1.34 on zanebot) — PASS
- "marketing page" (scrapper): **warns, best cos 0.45** (predicted band 0.43-0.46).
- "grok oauth refresh token fix": silent, rank 1 unchanged, score 0.6746 (recency 0.5 slightly lifts matched scenes as expected).
- "weekend plans" (marvin, 31 scenes): warns, best cos 0.44 — cosine floor covers small corpora, confirming z retirement safe.
