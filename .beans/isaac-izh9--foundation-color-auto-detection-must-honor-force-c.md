---
# isaac-izh9
title: Foundation color auto-detection must honor FORCE_COLOR — the missing third half of nfch
status: todo
type: bug
created_at: 2026-07-07T21:34:50Z
updated_at: 2026-07-07T21:34:50Z
---

isaac-nfch delivered its two halves: cli-proxy forwards stdout-tty in the start frame; cli-server sets FORCE_COLOR=1 on the spawned subprocess (dispatch.clj:44). But no isaac code READS FORCE_COLOR: the auto color path resolves via isaac.cli.table (foundation table.clj:10) -> isaac.cli.color/tty? -> (System/console), which is nil under a pipe — so the spawned command stays colorless and the whole hint chain is a no-op end to end. Verified 2026-07-07 (bare 'zane-isaac sessions list' colorless with both nfch halves deployed; --color always works). Fix in isaac-foundation: the tty?/auto decision honors FORCE_COLOR (and while there: the ecosystem conventions CLICOLOR_FORCE and NO_COLOR), and the THREE independent detectors should unify on it — isaac.cli.color/tty? (table.clj consumer), config/cli/common.clj stdout-tty? (:114), log_viewer.clj (:150). Note the verify gap that let nfch pass: its scenarios asserted the env lands on the spawn, not that color emerges end-to-end — add at least one assertion at the color-decision level (foundation spec: tty? true when FORCE_COLOR=1 despite no console). Deploy note: foundation ships via the brew train (tag + release + brew upgrade on zanebot), not a module bump.
