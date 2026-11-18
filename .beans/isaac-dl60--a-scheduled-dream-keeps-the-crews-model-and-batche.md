---
# isaac-dl60
title: A scheduled dream keeps the crew's model, and batches have a character cap
status: completed
type: bug
priority: normal
created_at: 2026-11-18T17:49:26Z
updated_at: 2026-11-18T18:55:38Z
---

Repo: **isaac-dream** (`src/isaac/dream/run.clj` `DreamService`/`schedule!`, `batches`).

## What is wrong

Zane's nightly dream has failed on 2026-11-17 and 2026-11-18 with
"closed". Each run sent one ~780k-character request to Grok, and the
connection dropped after about two minutes. The watermark stays at
2026-11-16, so every night's window is bigger. Two causes:

1. **Wrong model.** `DreamService` `on-load` calls
   `(schedule! (assoc cfg :crew {crew {:dream dream}}))`. That replaces
   the whole `:crew` map with the dream entry alone, so the scheduled run
   loses the crew's `:model` (Zane: `:micah-opus`) and falls back to
   `defaults.crew.model` (`:grok-4-7`). The crew's soul and anything else
   under the crew is lost the same way.
2. **No size cap.** A batch is `dream.batch` messages (default 400), with
   no character bound. Zane's messages are long, so 400 of them is ~780k
   characters.

## Fix

- The scheduled run resolves the crew from live config, the same way
  `isaac dream run --crew` does: crew model, soul and dream settings.
- New setting `dream.batch-chars`: a batch also ends before its window
  lines exceed this many characters. Default 200000. A single line longer
  than the cap goes alone in its own batch. Add it to the dream settings
  schema.

## Acceptance

- `features/dream/schedule.feature` "a scheduled dream dreams on the crew's own model" passes.
- `features/dream/history.feature` "a batch also ends at dream.batch-chars characters" passes.
- The existing batch scenarios still pass.
- `bb ci` green.

After deploy (planner): Zane's crew config has `:prompt-chars 16000`
under `:dream`, which nothing reads; replace it then.

feature-baseline: isaac-dream ee26ab56224c87833771ef500b9097fbcd482189
feature-blob: isaac-dream features/dream/schedule.feature a8eebf4eb2fbc5b1d43e7ebf54b96e14df4ce450 105
feature-blob: isaac-dream features/dream/history.feature ff5e5993e0bcd76f4619f6ea21cbfc865f972265 126

## Merge conflict (2026-11-18)

Gate PASS at isaac-dream bean/isaac-dl60 fadf0a8; `bb ci` green (63 specs, 80 features). After `git fetch origin`, `git rebase origin/main` conflicted in `src/isaac/dream/run.clj` against upstream `8ec4d5671248bdce23ebeb32a63730c6568fadb4`. Rebase aborted; the pushed bean branch remains intact. Planner must reconcile the overlapping implementation and return the bean for rebase, test, gate, and landing.

Retry in bean-isaac-dl60: fetched origin/bean/isaac-dl60 fadf0a8 into an isolated worktree and retried `git rebase origin/main`; the same conflict remains in `src/isaac/dream/run.clj`. Upstream moved `settings` to `digest/settings` and added scheduled `digest/send!`, while this bean adds the batch-chars default to `settings` and changes scheduled `run!` to load live config. Rebase aborted without resolving the overlap; planner reconciliation is still required before landing.

## Landed on main (2026-11-18)

Planner reconciled the branch against upstream digest; `bb ci` green (63 specs, 81 features). Gate PASS on bean branch and squash commit. The earlier merge conflict is resolved.

main-sha: isaac-dream 59aebd0295ff6b1f95b9ef0bea924ae09a27c05e
