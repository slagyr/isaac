---
# isaac-dzqx
title: 'isaac-hail pins an incoherent set: isaac-http 32603e6 requires isaac-foundation 9586b08, hail pins 9ab2527'
status: todo
type: bug
priority: high
created_at: 2026-09-24T22:51:29Z
updated_at: 2026-09-24T22:51:29Z
---

Repo: **isaac-hail**.

## Found by the check it motivated

isaac-57rl's new `bb pins` set check was dry-run against the real fleet and
isaac-hail is the one repo that **fails**, not merely lags:

    isaac-hail pins isaac-http 32603e6
    isaac-http 32603e6 requires isaac-foundation 9586b08
    isaac-hail pins isaac-foundation 9ab2527

That is the isaac-v2x1 failure mode exactly — foundation and isaac-agent /
isaac-http moved as a set and one repo took part of the move. It is not a false
positive; it is a genuinely drifted repo the check would now stop.

What that buys in practice: isaac-hail's suite is exercising a combination no
host installs, so a failure there may not reproduce anywhere real, and a pass
there does not mean much either. isaac-a9dp is the worked example of what that
costs when nobody notices.

## Acceptance

- isaac-hail's isaac-foundation / isaac-agent / isaac-http pins move to a
  coherent set as a group, and `bb pins` exits 0.
- `bb ci` green on the new set. Expect fixture work: isaac-ruom retired the
  flat `:defaults` keys, so any fixture writing `:defaults {:crew …}` or
  `:defaults {:model …}` needs the new shape — see isaac-v2x1 (`d7a1447`) and
  isaac-01kv (`6df59f7`) for worked migrations, and check `src/` for reads of
  the retired keys, not just fixtures.
- While there: isaac-hail is a comm-adjacent module, so confirm whether it
  needs the isaac-agent repin that carried isaac-clba's delivery audit logging.

## Notes

The registry itself was mid-train when this was measured — `isaac.agent
8cfd44d` requires foundation `9ab2527` while `isaac.http 76936f8` requires
`fae35d6` — so there was no single coherent fleet foundation to name at that
moment. Pick the set deliberately rather than chasing whatever the registry
says on the day.
