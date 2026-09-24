---
# isaac-od6i
title: Drop hardcoded main crew fallbacks in isaac-episodes
status: completed
type: task
priority: normal
tags:
    - episodes
created_at: 2026-09-16T15:49:57Z
updated_at: 2026-09-24T23:46:25Z
blocking:
    - isaac-zule
blocked_by:
    - isaac-bfwn
---

isaac-episodes still falls back to a hardcoded `"main"` crew in policy, recall, migrate, cli, layout, and lifecycle. After defaults.crew is required (parent bean), those fallbacks must use `:defaults :crew` or the session’s `:crew`, never `"main"`.

## Decisions

- Decision (2026-09-16, Micah): same cutover as the agent bean — no production `"main"` crew identity.
- Blocked until the agent bean lands (schema + charge/session stamp).

## Scope (isaac-episodes)

Replace `(or crew "main")` / `(or (:crew session) "main")` / `resolve-crew-context cfg "main"` with session crew or `(get-in cfg [:defaults :crew])`.

## Scenarios

No new Gherkin in this repo. Parent bean owns the user-visible contract. This bean is grep + specs.

## Acceptance

```
cd isaac-episodes
bb spec
bb ci
```

One-time: `git grep -n '"main"' -- src` has no crew-identity fallback.

## Implementation (2026-09-24, scrapper@isaac-work-3)

Branch: isaac-episodes `bean/isaac-od6i` @ 7cd828d (pushed; not squashed to main — ungated bean, verify path).

- New `isaac.episodes.crew/resolve-id`: entity crew (blank = unnamed), else
  `config-defaults/crew-id` from cfg (live snapshot when cfg nil), else nil.
  Spec: `spec/isaac/episodes/crew_spec.clj`.
- Rewired lifecycle (7 sites), migrate, layout, policy/episodes (3), recall inject
  to `resolve-id`; cli/recall-cli/recall-tools just drop the trailing `"main"`;
  gist model resolution uses `(defaults/crew-id cfg)` instead of `"main"`.
- `git grep -n '"main"' -- src` → no matches.
- `bb spec` 223/0, `bb ci` EXIT 0 (spec 223/0, features 85/0).
- Gate: `bb bean-gate verify isaac-od6i` → exit 2 (no feature-baseline).

## Landed on main (2026-09-24)

main-sha: isaac-episodes 9965aebe260f989a4665b60d54985f221d64c7cd

Verified by perceptor@isaac-verify: bean/isaac-od6i @ 7cd828d (based on origin/main 01b538a); bb ci EXIT 0 (spec 223/0/599, features 85/0/543); git grep '"main"' -- src -> no matches; pins agent 8cfd44d / foundation 9ab2527 on origin/main. Squash tree == branch tip; branch deleted.
