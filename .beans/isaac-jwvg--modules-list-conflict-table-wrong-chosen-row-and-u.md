---
# isaac-jwvg
title: 'modules list conflict table: wrong ✓ chosen row and undifferentiated severity'
status: todo
type: bug
priority: normal
created_at: 2026-07-02T22:55:55Z
updated_at: 2026-07-02T23:10:23Z
---

## Problem (investigated 2026-07-02, planner)

`isaac modules list` on zanebot showed `isaac.agent 0.1.5 ✓ loaded` while the runtime classpath actually serves the registry-configured agent 0.1.6 (verified: `modules deps --classpath` → gitlibs a6947c4e). The conflict table lies about what loaded.

Root cause, `isaac-foundation src/isaac/module/loader.clj`:

- `module-version-conflicts` (~line 1360) computes the winner by `(reduce prefer-module-coord (map :coord reqs))` over only the **dep-walk requests** — the explicit registry/config coordinate never enters the contest, though real resolution (`plan-module-classpath-pairs`) always prefers explicit coords (implied pairs are computed only for ids NOT explicitly configured).
- `prefer-module-coord` (~line 284) breaks ties by **lexicographic SHA comparison** — meaningless ordering; its use in the conflict table means the ✓ lands on an arbitrary sibling pin.
- Docstring claims ":chosen matches prefer-module-coord / unified resolution" — false for explicit modules.

Also: the warning treats all pin divergence alike. A sibling pin OLDER than the loaded version is routine drift (info at most); a pin NEWER than loaded means a consumer expects code that is not running — that is the case worth a loud warning.

## Desired outcome

- The ✓/chosen row reflects what unified resolution actually loads (explicit coord wins when configured).
- Severity split: pins older than the chosen version render as quiet drift info (or are summarized); pins newer than chosen render as the ⚠ warning.
- With zanebot's current state (agent 0.1.6 explicit; acp+4 pins→0.1.5; imessage pin→0.1.6; server 0.1.7 explicit; cli-server pin→0.1.0) the output shows no ⚠ warnings.

## Acceptance criteria

- [ ] Spec: conflict table chosen version equals the explicitly-configured module version when present (regression for the wrong-✓).
- [ ] Spec/feature: newer-than-chosen pin ⇒ warning row; older-than-chosen pin ⇒ drift info, not ⚠.
- [ ] `bb spec` / `bb features` green in isaac-foundation.
- [ ] Docstring on module-version-conflicts corrected.

## Likely repo scope

isaac-foundation (module/loader.clj + CLI rendering).
