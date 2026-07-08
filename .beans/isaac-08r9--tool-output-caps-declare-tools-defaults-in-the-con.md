---
# isaac-08r9
title: 'Tool output caps: declare :tools :defaults in the config schema; halve the code defaults'
status: todo
type: task
priority: normal
created_at: 2026-07-08T23:13:08Z
updated_at: 2026-07-08T23:13:08Z
---

## Goal

Two fixes to the tool output-cap knobs (isaac-agent), from the 2026-07-08 efficiency review:

1. **Schema gap**: the code reads config at [:tools :defaults :max-lines / :max-bytes] (registry.clj snapshot-caps) but the config schema never declares those paths — isaac config set rejects them ('unknown path: tools.defaults.max-lines (unrecognized segment: defaults)') while config validate silently tolerates a hand-edited value. Declare both keys (ints) so config set / validate know them.

2. **Halve the defaults** (output_cap.clj): default-max-output-lines 2000 -> 1000; default-max-output-bytes 262144 -> 131072. Micah-directed. Rationale: a single 256KB tool result is ~65K tokens of context; observed results at 170-200KB drove compaction pressure and a compaction-chunk-infeasible warning on zanebot.

Note: zanebot currently carries an explicit override (400 lines / 32KB) in isaac.edn, so this deploy does not change zanebot behavior — the new defaults protect everyone else.

## Scenarios (worker writes; required coverage)

1. isaac config set tools.defaults.max-lines 500 succeeds and the value lands (CLI feature, bands.feature style).
2. With no config override, a tool result exceeding 1000 lines truncates head-tail at the new default (update existing output-cap specs' numbers — do not weaken the head-tail marker assertions).

## Acceptance

- [ ] Scenarios green; existing output-cap spec numbers updated to the halved defaults
