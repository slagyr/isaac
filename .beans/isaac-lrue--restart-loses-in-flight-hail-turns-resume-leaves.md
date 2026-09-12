

## Planner adjustment (2026-09-12, prowl@isaac-plan) — conflict resolve: focused resume/sweep gates control; drop full `bb ci`

Conflict: implementation on agent `bean/isaac-lrue` @ `3a1cbff` and hail `bean/isaac-lrue` @ `0bca2f6` is complete; focused features and full specs are green. The bean also names `bb ci` in both repos. Those full gates are red on **origin/main independently of this bean**.

**Decision: focused gates control. Do not sequence isaac-kwhb (or hail band/config repairs) in front of this bean.** This bean is the restart-loss defect (legacy marker path + stray sweep eating just-requeued deliveries). Waiting on kwhb leaves that hole open. Do not absorb kwhb's 19 feature failures or hail's 13 band/config failures.

### Ambient owners (not this bean)

- **isaac-kwhb** (in-progress) — agent main 19 feature failures / 180s timeout (`:isaac/component` berth not declared). Same on origin/main `374ea9e`.
- **isaac-9bno** (todo) / **isaac-39vi** (draft) — hail band-inheritance + config-validate + templating reds against current agent pins. Hail main cannot resolve unpublished agent pin `86a9a92`; with this bean's public pin, 13 pre-existing band/config failures remain (focused reruns reproduce 6; prompt templating passes in isolation).

Clean branch-vs-main: this bean adds no failure.

### Controlling acceptance (supersedes `bb ci` in both repos)

**isaac-agent** `bean/isaac-lrue` @ `3a1cbff` (or rebased equivalent):

    bb features features/bridge/suspend.feature features/session/resume_repair.feature
    bb spec

0 failures. A suspended hail marker at the **legacy** path is requeued **and** the legacy file is gone (no marker at either path).

**isaac-hail** `bean/isaac-lrue` @ `0bca2f6` (or rebased equivalent):

    bb features features/turn-resume.feature features/delivery.feature
    bb spec

0 failures. A delivery requeued by resume with its marker still on disk is **not** swept on the next tick; a genuine claim-crash stray is still removed and posts attention.

Do **not** require:
- agent `bb ci` / full `bb features` exit 0
- hail `bb ci` / full `bb features` exit 0
- landing kwhb first
- repairing hail band-inheritance / config-validate in this bean
- unpinning hail to hide the 13

Standing rule: name the runner and files that measure the bean. File ambient full-suite reds separately. Do not weaken the legacy-marker-clear or hails-never-die contracts.
