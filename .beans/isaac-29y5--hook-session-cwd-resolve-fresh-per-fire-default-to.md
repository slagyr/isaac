---
# isaac-29y5
title: 'Hook session cwd: default to crew quarters at session creation'
status: completed
type: feature
priority: high
created_at: 2026-05-14T15:30:01Z
updated_at: 2026-05-14T17:59:06Z
---

Hook sessions today get whatever cwd was current at first session creation — the process cwd of the `bb` invocation that first fired the hook. Symptom: `hook:location` session on zanebot carries `:cwd "/Users/zane/Projects/isaac/isaac-live"` from a worktree the server hasn't run in for hours. The cwd should never have been the server's launch dir; it should be the crew's quarters.

## Desired behavior

**On hook session creation**, set `:cwd` to the crew's quarters (`<state-dir>/crew/<crew-id>/`). If the quarters dir doesn't exist, create it.

**On subsequent hook fires**, leave the session's `:cwd` alone. Hook sessions behave like any other session: created with a sensible default; the user can change cwd later (e.g., manually editing the session edn).

Existing hook session files on disk with a wrong `:cwd` are not migrated. If the user cares, they fix by hand. The bug stops being created for new hook sessions.

## What this is NOT

- ~~Frontmatter `:cwd` field on the hook schema.~~ YAGNI — every existing hook uses absolute paths in its prompt. Add the field if/when a concrete need shows up.
- ~~Resolve cwd fresh on every fire.~~ Sessions store cwd; that's how every other session works. Overwriting on each fire would be inconsistent with interactive sessions.
- ~~Fallback chain.~~ Quarters always exist (auto-mkdir on use). No fallback case to spec.

## Acceptance Criteria

- New hook session creation sets `:cwd` to crew quarters at `<state-dir>/crew/<crew-id>/`, auto-creating the dir if missing.
- Subsequent hook dispatches on the same session do not modify `:cwd`.
- Quarters auto-creation is idempotent (mkdir -p semantics; not "fail if exists").
- The process cwd (where `bb` was launched) is irrelevant to hook session cwd.

## Scenarios

```gherkin
Scenario: New hook session defaults cwd to the crew quarters
  Given the isaac file "config/hooks/lettuce.md" exists with:
    """
    ---
    {:crew :main
     :session-key "hook:lettuce"}
    ---

    Test message.
    """
  And crew "main" has quarters
  When a POST request is made to "/hooks/lettuce":
    | key                  | value            |
    | body                 | {}               |
    | header.Authorization | Bearer secret123 |
  Then the response status is 202
  And session "hook:lettuce" matches:
    | key | value                              |
    | cwd | target/test-state/.isaac/crew/main |

Scenario: Existing hook session keeps its own cwd
  Given the isaac file "config/hooks/lettuce.md" exists with:
    """
    ---
    {:crew :main
     :session-key "hook:lettuce"}
    ---

    Test message.
    """
  And the following sessions exist:
    | name         | crew | cwd           |
    | hook:lettuce | main | /tmp/my-place |
  When a POST request is made to "/hooks/lettuce":
    | key                  | value            |
    | body                 | {}               |
    | header.Authorization | Bearer secret123 |
  Then the response status is 202
  And session "hook:lettuce" matches:
    | key | value         |
    | cwd | /tmp/my-place |
```

## New steps to implement

- `Given crew {crew:string} has quarters` — mkdir `<state-dir>/crew/<crew>/`. Used in scenario 1.

No other new steps. The existing `session "<key>" matches:` (session.clj:1208) handles the cwd assertion; the existing `the following sessions exist:` (session.clj:1195) already accepts a `cwd` column.

## TODO

- [ ] Hook handler in `server/hooks.clj`: set `:cwd` to crew quarters on `store/open-session!` for webhook-origin sessions
- [ ] Auto-create quarters dir if missing (mkdir -p)
- [ ] Add `Given crew {crew:string} has quarters` step
- [ ] Two scenarios in `features/server/hooks.feature` (or a new `features/server/hook_cwd.feature` if it fits better)

## Related

- The misleading `cwd "/Users/zane/Projects/isaac/isaac-live"` was the secondary observation in the hook-debugging session of 2026-05-14. The primary symptom (allowed-tools=0) was a separate bug in `server/hooks.clj` (`with-system` vs `with-nested-system`) — independent of this bean.



## Verification failed

Feature tampering check passed, `bb spec` passed, and the two hook-cwd scenarios in `features/server/hooks.feature:91` and `:112` passed with clean output. No blocking smell was introduced in the new hook spec or steps.

The bean failed the test-speed gate. The relevant feature run finished in 50.85ms for 2 examples, or about 25.43ms/example. `.verify-baseline.edn` currently sets the feature baseline to 11.015ms/example, so this run is about 2.31x baseline and exceeds the allowed 1.5x threshold. Reopening for review of the regression or for an updated baseline strategy if this global feature baseline is too coarse for server-hook scenarios.



## Verification failed

Re-verified after commit `980ba1ac` ("Speed up hook cwd verification scenarios"). The feature edit appears legitimate: it keeps the same two assertions and only reuses the existing background hook config instead of redefining the hook inline. `bb spec` passed, and `bb features features/server/hooks.feature:37 features/server/hooks.feature:48` passed with clean output.

This still misses the test-speed gate. The updated feature run finished in 34.10ms for 2 examples, or about 17.05ms/example. The current feature baseline is 11.015ms/example, so the allowed 1.5x ceiling is about 16.52ms/example. This is much better than the prior 25.43ms/example run, but it is still slightly over the threshold, so I cannot pass it yet.



## Verification failed

Re-verified after commit `9ac95e29` ("Trim hook cwd scenario setup overhead"). The feature change is acceptable: it only removes the redundant `Given crew "main" has quarters` step from the first scenario, which is consistent with the bean's own acceptance text that quarters auto-creation should be idempotent and happen automatically. `bb spec` passed, and `bb features features/server/hooks.feature:37 features/server/hooks.feature:47` passed with clean output.

The bean still misses the test-speed gate. The updated feature run finished in 34.78ms for 2 examples, or about 17.39ms/example. The current feature baseline is 11.015ms/example, so the allowed 1.5x ceiling is about 16.52ms/example. This is still over threshold, so I cannot pass it yet.
