---
# isaac-ff2r
title: 'isaac-dream: dream run --since/--until, reads a big window in batches'
status: in-progress
type: feature
priority: normal
created_at: 2026-11-02T14:30:10Z
updated_at: 2026-11-02T14:31:10Z
parent: isaac-pcm3
---

Bean 3 of dreaming (after isaac-b1ir, isaac-p36b). Micah wants Zane to dream once, by hand, over most or all of his history, before turning on scheduled dreaming.

Today a dream can't do that. `dream run` reads only since the watermark, takes the **oldest 400** messages of the window, then moves the watermark to the dream's start time. Zane has ~1,550 dreamable messages (2026-06-27 → now, ~1M chars), so a first dream would read June to early September and mark the rest as dreamed without reading it.

## What changes

- `isaac dream run --crew <id> [--since <t>] [--until <t>]`. `<t>` is an ISO instant or a date (`2026-06-01` = midnight UTC). `--since` replaces the watermark as the window start for this run; `--until` replaces "now" as the end. An unreadable value is refused (`invalid --since: <value>`, exit 1) before any model call.
- **Batches.** The window is read in batches of `dream.batch` messages (crew config, under `defaults.crew.dream` too; default 400), one model call per batch, oldest first, until the window is done. Labels (`<session>#<n>`) keep counting across batches. Each batch's prompt lists current habits, including ones proposed earlier (in this run or before) with their state, so a later batch can reinforce instead of re-proposing.
- **Reinforcing a proposed habit** appends a `reinforced` history event and keeps its state. (Today reinforce only touches active habits.)
- **Watermark.** On success it moves to the window's end (`--until` or the start time). After a failed batch it moves to the timestamp of the last message the earlier batches read, so the next run resumes there. It never moves backward.
- **Diary.** One entry per run: `window {:since :until}` is the effective window (`--since` when given), plus `batches` (number completed); `proposed`/`reinforced` cover all batches. A failed run still lists what earlier batches proposed.
- Digest: one per run, as today (proposals across batches, or the failure).

## Notes for the implementer

- Scenarios: `features/dream/history.feature` in isaac-dream. Matcher: blank cell = nil.
- Add `:batch {:type :int}` to the crew dream schema in `resources/isaac-manifest.edn`; bump the manifest `:version`.
- Don't cut a batch between two messages with the same timestamp (the watermark compare is strictly-after), or note why it can't happen.
- Update the handbook (`resources/isaac/dream/handbook.md`) and README: `--since`/`--until`, batches, `dream.batch`, the watermark rules, reinforcing proposed habits.
- `isaac` `modules.edn`: bump `:isaac.dream` to the landed sha.

## After landing (planner, not the worker)

Install isaac-dream on zanebot, then `isaac dream run --crew zane --since 2026-06-01` once and review `isaac dream list --crew zane --state proposed`. Scheduled dreaming stays off for Zane.

## Acceptance

Run from `isaac-dream`, with `@wip` removed from every scenario listed:

- `bb features features/dream/history.feature`
- `bb ci` green locally, and the `CI Tests` workflow green on isaac-dream `main` after landing.
- Unit specs cover since/until parsing, batch splitting (including same-timestamp boundaries), the watermark rules (success, partial failure, never backward), and reinforcing a proposed habit.
- `isaac` `modules.edn` `:isaac.dream` points at the landed sha.

feature-baseline: isaac-dream e22177c197c150fc0e0cfe7f2bab1c0671a77d7a
feature-blob: isaac-dream features/dream/history.feature 86956478c7f23b7f7aed5c676c85436e2ad16924
