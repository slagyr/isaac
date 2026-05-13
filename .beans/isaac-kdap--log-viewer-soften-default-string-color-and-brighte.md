---
# isaac-kdap
title: 'Log viewer: soften default string color and brighten zebra bg'
status: completed
type: task
priority: normal
tags:
    - unverified
created_at: 2026-05-13T03:17:50Z
updated_at: 2026-05-13T03:20:35Z
---

## Change

Two small tweaks to the log viewer color palette so output is more
neutral and zebra striping is visible regardless of terminal theme:

- **Default string values**: today `color-for-value` returns `""` for
  strings, which lets the terminal foreground take over — usually
  bright white on dark themes, which clashes. Set explicit
  `38;5;250` (soft light gray).
- **Zebra background**: `bg-zebra` is currently `48;5;236`, which
  blends into many dark terminal backgrounds. Brighten to `48;5;238`
  for clear contrast.

## Files

- `src/isaac/log_viewer.clj`:
  - `color-for-value`: change `:else ""` → `:else (ansi "38;5;250")`.
  - `bg-zebra`: change `(ansi "48;5;236")` → `(ansi "48;5;238")`.

## Specs / features to update

- `spec/isaac/log_viewer_spec.clj`:
  - "color-for-value returns empty string for strings" — flip to assert
    the new 256-color form (`38;5;250`).
  - The zebra tests assert `48;5;236` literal — update to `48;5;238`.
- `features/cli/logs.feature`:
  - "Zebra striping is on by default" asserts `the stdout contains "48;5;236"` — update to `48;5;238`.
  - "--no-zebra disables row striping" asserts `does not contain "48;5;236"` — update to `48;5;238`.

## Acceptance

- `bb spec spec/isaac/log_viewer_spec.clj` green.
- `bb spec` green overall.
- `bb features features/cli/logs.feature` green.
- Manual eyeball: `isaac logs` shows soft-gray strings (not bright
  white) and clearly-visible zebra striping on a dark terminal.

## Summary of Changes

- bg-zebra: 48;5;236 → 48;5;238 (brighter, visible on more terminals)
- color-for-value strings: "" → (ansi "38;5;250") (soft light gray instead of terminal default)
- Updated log_viewer_spec.clj and logs.feature to match new codes
