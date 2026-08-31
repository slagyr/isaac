

## Planner adjustment (2026-08-31, prowl@isaac-plan) — verify-fail attempt 2

**Decision: keep the migrated bulletin scenarios controlling. Drop the rest of `compaction_logging.feature` and the full-suite/180s gate from this bean. Rebase onto current main (x2up landed) before re-verify.**

### Split of the 8 compaction_logging failures

Verifier: main has **6** failures on the same targeted command; the bean branch has **8**. The two extras are exactly the scenarios 5nxf un-`@wip`'d:

| Line | Scenario | Whose |
|---|---|---|
| :38 | Chat logs the compaction trigger (Then bulletin `compaction/start`) | **5nxf** — migrated from `on-compaction-*`; was `@wip` on main |
| :104 | Compaction failure is logged (Then bulletin start/failure) | **5nxf** — same; was `@wip` on main |
| :64 :87 :141 :215 :279 :361 | preserve user message, chat completes, oldest-only, no re-trigger, max-attempts, toolCall pairing | **Not 5nxf** — ambient p9zy/x2up/qkqm; main already red |

Do **not** hold scuttlebutt on toolCall pairing or max-attempts. Those stay on **isaac-x2up** (completed) / **isaac-qkqm** (draft suite health).

### Why :38 / :104 can still be red after x2up

Those fixtures still seed `total-tokens 95` on a 200 window (threshold 160) unless x2up already raised them on the branch you rebased. Compaction never fires → bulletin never fires → Then fails even if `on-bulletin` is wired.

**Worker:** rebase `bean/isaac-5nxf` onto current `origin/main` (x2up complete). If those two scenarios still have `total-tokens 95`, raise `last-input-tokens` / `total-tokens` **above** `0.8 * window` — same pin as x2up; do not inflate English; do not weaken bulletin Then tables.

Then prove bulletins: memory comm `event=bulletin` `kind=compaction/start` (and failure path `compaction/failure`). That is 5nxf product. If compaction fires and events are still `compaction-start` not `bulletin`, the migration is incomplete — fix emission, don't rescope again.

### Acceptance (supersedes the broad compaction_logging + full bb features lines)

On isaac-agent at a SHA containing 5nxf (CliComm gone, scuttlebutt un-`@wip`):

```
bb spec spec/isaac/comm spec/isaac/drive
bb features features/comm/scuttlebutt.feature features/comm/memory.feature
bb features features/session/llm_interaction.feature
bb features features/session/compaction_logging.feature:21
bb features features/session/compaction_logging.feature:93
```

(Use the scenario start lines if `:38`/`:104` drift; the two scenarios are "Chat logs the compaction trigger…" and "Compaction failure is logged…".)

- 0 failures on those commands.
- Known pre-existing: memory.feature "Compaction triggers during a memory comm turn" (`:70`) still allowed red / listed.
- **Do not** require the other six compaction_logging scenarios, huge-head unless it is a bulletin-row migration that is still `@wip` on your branch, or full `bb features` / 180s.

### Out of scope

- x2up fixture philosophy (already decided: last-input seeds, never `(str map)`).
- isaac-qkqm (max-attempts + toolCall pairing on main).
- Module comms (acp/discord/imessage).

### Handoff

Back to **work** (not verify): rebase, green the two bulletin scenarios, retag unverified. Verify-fail counter reset by this note.
