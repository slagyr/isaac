---
# isaac-jwvg
title: 'modules list conflict table: wrong ✓ chosen row and undifferentiated severity'
status: in-progress
type: bug
priority: normal
created_at: 2026-07-02T22:55:55Z
updated_at: 2026-07-02T23:29:16Z
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

- [ ] Spec: chosen/loaded version equals the explicitly-configured module version when present (regression for the wrong-✓).
- [ ] `--edn` / `--json`: divergent requests are severity-partitioned; `:conflicts` means requested versions newer than `:chosen`, `:drift` means requested versions older than `:chosen`.
- [ ] Human `modules list`: newer-than-chosen requests render only in the ⚠ conflicts block; older-than-chosen requests render only in the ℹ drift block and never under ⚠.
- [ ] `bb spec` / `bb features` green in isaac-foundation.
- [ ] Docstring on module-version-conflicts corrected.

## Likely repo scope

isaac-foundation (module/loader.clj + CLI rendering).

## Planner clarification (2026-07-02, prowl)

This bean requires a **structured severity split**. Do not keep the old flat
`:conflicts` meaning "all divergent pins".

### Machine output contract (`modules list --edn` / `--json`)

Use two top-level collections with the same grouped shape:

```edn
{:conflicts [{:id :isaac.server
              :chosen "0.1.0"
              :requested [{:version "0.1.7"
                           :required-by [:isaac.cli-server]}]}]
 :drift [{:id :isaac.agent
          :chosen "0.1.6"
          :requested [{:version "0.1.5"
                       :required-by [:isaac.comm.acp
                                     :isaac.hail
                                     :isaac.cron]}]}]}
```

Rules:

- `:chosen` is the **actual loaded version** from unified resolution.
- `:conflicts` contains only requests whose version is **newer** than `:chosen`.
- `:drift` contains only requests whose version is **older** than `:chosen`.
- `:requested` contains only the divergent requests for that severity bucket;
  requests equal to `:chosen` are omitted.
- A module may appear in **both** `:conflicts` and `:drift` if different
  requesters pin both newer and older versions than `:chosen`.
- Quiet-by-default behavior stays: omit a top-level key entirely when that
  bucket would be empty.

### Human output contract (`modules list`)

Render separate blocks below the main modules table:

- `⚠ <n> version conflict(s) — requested newer than loaded`
- `ℹ <n> version drift(s) — loaded version is newer than some requests`

Both blocks use the yi82 columns:

```text
MODULE  VERSION  REQUIRED BY  LOADED
```

Rendering rules:

- For each grouped entry, render the chosen/loaded row once with `✓`.
- Then render the divergent `:requested` rows for that severity bucket without
  `✓`.
- If a module appears in both buckets, it may appear in both blocks.
- Older-than-chosen rows never appear in the ⚠ block.

### Current-state expectation

On zanebot's current module set, the agent-version divergence is drift-only.
That means the output may show an ℹ drift block, but it must show **no ⚠
conflicts block** for the 0.1.5 agent pins against loaded 0.1.6.



## Verification failed

HEAD: 9822bbbf62c0c24adbe17760ac471c30611ffbfa
Working tree: clean

Failing acceptance criterion: machine output contract is not implemented as specified. The bean requires severity-partitioned top-level collections in `modules list --edn / --json`: `:conflicts` for newer-than-chosen requests and `:drift` for older-than-chosen requests, omitting empty buckets.

Evidence:
- In `isaac-foundation` commit `fe2e1c0`, `src/isaac/module/loader.clj` still returns a single flat `:conflicts` collection with per-request `:severity` tags; there is no top-level `:drift` bucket.
- `src/isaac/modules/cli.clj` still destructures only `{:keys [modules conflicts]}` from `list-configured-modules` and prints structured output as `{:modules modules :conflicts conflicts}`. No `:drift` key is emitted.
- Fresh reproduction in `isaac-foundation`:
  `bb isaac --root /tmp/isaac-jwvg-root modules list --edn`
  returned:
  `{:modules [...], :conflicts [{:id :marigold.shared, :chosen "1.0.0", :requested [{:version "1.0.0", :required-by [:marigold.app.conflict], :severity :drift} {:version "9.9.9", :required-by [:marigold.app2.conflict], :severity :warning}]}]}`
  This violates the planner clarification and acceptance criterion that older-than-chosen requests must appear under top-level `:drift`, not inside `:conflicts`.

What I validated:
- `bb spec` in `isaac-foundation` passed: 809 examples, 0 failures, 1426 assertions.
- `bb features` in `isaac-foundation` passed: 124 examples, 0 failures, 299 assertions.
- The chosen-row bug itself appears fixed, and human text rendering has separate warning/drift blocks.

What still needs fixing:
- Change machine output to emit separate top-level `:conflicts` and `:drift` collections per the bean contract.
- Update features/specs to assert that structure directly.
- Keep quiet-by-default omission of empty buckets.
