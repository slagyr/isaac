---
# isaac-39vi
title: 'Suite health (isaac-hail): band-inheritance + hail-band-prompts nil on agent ac1bf9b pin'
status: draft
type: feature
priority: high
tags:
    - suite-health
created_at: 2026-09-10T22:55:38Z
updated_at: 2026-09-10T22:55:38Z
---

Ambient hail CI failures that appeared **after** pinning isaac-hail to agent `ac1bf9b9` (isaac-jejt pin-only commit `fac43ef`). **Not jejt product.** Do not reopen **isaac-jejt**.

## Observed (2026-09-10, perceptor@isaac-verify)

GitHub Actions hail CI Tests run 34539090442 on `fac43ef`: 146 examples, **7 failures**.

1–4. Hail band inheritance via base template bands — Expected truthy/maps/strings, got **nil**
5–7. Hail band prompt templating with params — Expected rendered prompt, got **nil**

https://github.com/slagyr/isaac-hail/actions/runs/34539090442

Contrast:
- CI 34535392748 on hail `9887de0` (agent pin **461082b8**, Linux 146/2): only the two jejt scenarios red (`delivery.feature:845`, `turn-resume.feature:123`). These 7 were green.
- Last green hail main: `6339b55` (CI 34531863683).
- Isolated local: `bb features features/band-inheritance.feature` → 7/0/16. Worker blamed local full-suite pollution; **Linux CI now disagrees** — after the pin the 7 fail on published CI.

`fac43ef` is pin-only (`deps.edn` + `bb.edn` isaac-agent / isaac-agent-spec → `ac1bf9b9`). jejt hail scenarios themselves are green (native 2/0/10; hail `bb spec` 156/0).

Related: **isaac-8ywz** (completed) is the band-inheritance product.

## This bean owns

Make band-inheritance and hail-band-prompts green on **published native CI** against the current agent pin, without weakening scenario intent and without recutting jejt cancel/archive.

1. Reproduce on Linux CI and locally with `ISAAC_GIT` unset (the published pin, not `:dev-local`).
2. Name the cause: agent-pin API drift, fixture pollution, step that reads a now-nil field, or suite-order leak that only shows on Linux.
3. Fix hail fixtures/steps or the compatibility surface. **Do not** weaken the inheritance/templating contract. **Do not** unpin jejt's agent SHA to hide the red.
4. If the cause is an agent behavior change outside hail, hail plan with the exact missing keys/symbols — do not silently absorb agent product.

## Acceptance

    cd isaac-hail
    # native, no ISAAC_GIT — published agent pin
    bb features features/band-inheritance.feature
    bb features   # or the hail-band-prompts feature file once named in the verify note

0 failures on each on GitHub Actions `bb ci` / `bb features` (the published gate). Isolated green is not enough — CI 34539090442 is the reproduction.

Do **not** reopen isaac-jejt. Do **not** require `sessions cancel` / `hail/cancelled/` work here.
