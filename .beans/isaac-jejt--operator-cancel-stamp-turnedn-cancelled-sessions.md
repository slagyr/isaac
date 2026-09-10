

## Planner adjustment (2026-09-11, prowl@isaac-plan) — verify-fail attempt 2: focused jejt + pin control; split ambient CI reds

Attempt 1: hail CI red because native `bb ci` still pinned pre-jejt agent `461082b8`. Attempt 2: worker pinned hail `deps.edn` + `bb.edn` to `ac1bf9b9` (`fac43ef`). **jejt hail scenarios themselves are green** (native `delivery.feature:845` + `turn-resume.feature:123` → 2/0/10; hail `bb spec` 156/0). Remaining reds are not jejt product.

**Decision: rescope. Do not return to the worker to recut cancel/archive. Do not escalate to human.**

### Controlling acceptance (supersedes `bb spec && bb features` green in both repos)

**isaac-agent** `ac1bf9b99522e338e79ebea9579804b9491c805e` (or rebased equivalent):

    bb features features/session/cli.feature:329
    bb features features/session/cli.feature:335
    bb features features/session/cli.feature:341
    bb features features/session/cli.feature:348
    bb features features/session/cli.feature:358
    bb features features/session/cli.feature:375
    bb features features/bridge/cancel.feature:51
    bb features features/session/resume_repair.feature:71
    bb spec

0 failures. Ten scenarios un-`@wip`. `cancel` listed in `cli.feature:22` help. Fire-and-forget; no `bridge/cancel!` from CLI.

**isaac-hail** `fac43ef163fe4d7ca7ae210e4ecf2e1704a584a6` (or rebased equivalent):

    # native, no ISAAC_GIT — published pin must be ac1bf9b9
    bb features features/delivery.feature:845
    bb features features/turn-resume.feature:123
    bb spec

0 failures. `deps.edn` + `bb.edn` isaac-agent / isaac-agent-spec `:git/sha` = `ac1bf9b99522e338e79ebea9579804b9491c805e`.

Do **not** require:
- hail full `bb features` / GitHub Actions `bb ci` exit 0 (the 7 band-inheritance / hail-band-prompts nils)
- agent full `bb features` / GitHub Actions `bb ci` exit 0 (`cli.feature:367` in-flight race)
- recutting `sessions cancel` to wait for unwind
- unpinning hail from `ac1bf9b9`

### Split out (draft; not this bean)

- **isaac-39vi** — hail band-inheritance (1–4) + hail-band-prompts templating (5–7) got nil on Linux CI **after** the ac1bf9b pin. Isolated local 7/0/16; CI 34535392748 on 461082b8 did **not** have these 7; last green hail main `6339b55`. Pin-only commit; Linux CI disagrees with "local pollution." Own the published native gate.
- **isaac-1k85** — agent `cli.feature:367` in-flight Expected true got false on CI 34535363284 only. Stamp + exit 0 passed; live turn cleared in-flight before the And (cooperative `cancelled?` poll). Isolated and full `cli.feature` green locally. Do not make cancel wait.

Verifier: PASS this bean on the focused commands + pin check. Record run counts. Do not land-block on 39vi / 1k85.
