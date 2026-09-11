---
# isaac-2eec
title: 'Suite health (isaac-episodes): embedding stderr + recall_logging event on foundation 8b4a33b pin'
status: draft
type: bug
priority: high
tags:
    - suite-health
created_at: 2026-09-11T21:13:49Z
updated_at: 2026-09-11T21:13:49Z
parent: isaac-3q4m
---

Ambient isaac-episodes feature reds under foundation pin `8b4a33b` (vs6f / oc3f train). **Not oc3f product.** Do not reopen **isaac-oc3f**. Do not treat origin/main's 67-red (`:isaac/component` berth not declared — **isaac-kwhb**) as proof of these three.

## Observed (2026-09-11, perceptor@isaac-verify)

`isaac-episodes` `bean/isaac-oc3f` @ `ec2fc54` (specs 205/0):

    bb features
    # 78 examples, 3 failures
    # recall/embedding.feature:88  — unknown provider stderr
    # recall/embedding.feature:100 — unknown source stderr
    # episodes/recall_logging.feature:53 — expected :recall/scene, got :drive/turn-accepted

Worker claimed pre-existing from the foundation pin advance. origin/main `19c48d6` (pin `e0dc789`) is 78 examples, **67 different failures** (agent `:isaac/component` berth not declared — kwhb). The bean's 3 are not those 67. Skill: pre-existing must be reproduced on origin/main to count — that bar cannot be met until kwhb, so this bean owns the 3 on the **8b4a33b** pin.

## This bean owns

Make the three named scenarios green on isaac-episodes against foundation `8b4a33b` (or later vs6f SHA) without weakening intent.

1. Reproduce isolated (`bb features <file>:<line>`) and in `bb features` on a checkout whose foundation pin is `8b4a33b`, with and without the oc3f episodes component-key diff.
2. Name the cause: embedding validation stderr drift, recall log event rename (`:recall/scene` vs `:drive/turn-accepted`), fixture pin, or oc3f component cutover.
3. If the cause is oc3f's component migration, hail plan — do not silently absorb it here as "suite health." If it is pin/fixture drift independent of the berth cutover, fix it here.
4. Do **not** weaken scenario intent. Do **not** `@wip` without a dedicated owner.

## Acceptance

    cd isaac-episodes
    bb features features/recall/embedding.feature
    bb features features/episodes/recall_logging.feature

0 failures on each, on the 8b4a33b (or later) foundation pin. Then `bb features` 0 on that pin, or remaining reds named with owning bean ids.

Do **not** reopen oc3f (production `isaac server` boot path, `:isaac.server/service` cutover). Do not require Discord/server/foundation gates here.
