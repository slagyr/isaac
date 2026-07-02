---
# isaac-fv1s
title: Generic fixture-named hails polluting the live hail root (undeliverable/hail-2,4,5.edn)
status: todo
type: bug
priority: normal
created_at: 2026-07-02T22:05:29Z
updated_at: 2026-07-02T22:05:29Z
---

## Problem

zanebot's LIVE isaac root contains test-fixture-shaped debris:

    ~/.isaac/hail/undeliverable/hail-2.edn
    ~/.isaac/hail/undeliverable/hail-4.edn
    ~/.isaac/hail/undeliverable/hail-5.edn

Names like `hail-N` match the fixture naming used across isaac-hail specs/features (e.g. hail-1, hail-9 in features). Real hails get 8-hex ids. Strong smell: some spec/feature/tool invocation is writing to the real `~/.isaac` instead of an isolated test root — a test-isolation leak that could someday corrupt real hail traffic or config.

## Desired outcome

- Root cause identified: which code path wrote fixture-named hails to the live root (spec run on zanebot? CLI defaulting root when env missing? delivery worker test?).
- The leak is fixed so test/spec/feature runs cannot write outside their test root.
- Debris removed from zanebot once explained.

## Acceptance criteria

- [ ] Written root-cause note in this bean (which path, when, why).
- [ ] Regression protection: spec or feature proving the offending path respects the isolated root (TDD applies to any code change).
- [ ] `~/.isaac/hail/undeliverable/` on zanebot cleaned of the fixture-named files (after root cause is captured).

## Likely repo scope

isaac-hail (and possibly foundation root-resolution).
