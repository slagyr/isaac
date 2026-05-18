---
# isaac-iu8v
title: Centralized CLI table formatter with color/zebra/--no-color
status: in-progress
type: feature
priority: normal
created_at: 2026-05-18T16:37:53Z
updated_at: 2026-05-18T16:39:15Z
---

## Description

Build a reusable `isaac.cli.table` namespace that formats tabular CLI
output with column alignment, optional zebra striping, optional ANSI
color, and a `--no-color` opt-out. The sessions command (and future
CLI subcommands) consume it instead of carrying their own ad-hoc
formatting code.

## Why

Today `isaac sessions` produces misaligned output (long discord session
ids push later columns off the grid) and has no color. Hand-rolling
this in every command bit-rots fast. One utility, one spec, every CLI
command renders consistently.

## API (proposed)

```clojure
(table/render
  {:columns [{:key :name  :header "SESSION" :align :left}
             {:key :age   :header "AGE"     :align :right :format age-str}
             {:key :tokens :header "USED"   :align :right :format ->commas}
             {:key :pct   :header "PCT"     :align :right
              :format    pct-str
              :color-fn  (fn [p] (cond (> p 100) :red
                                       (>= p 80) :yellow
                                       :else     nil))}]
   :rows    [...session maps...]
   :zebra?  true
   :color?  true})           ; nil → autodetect from stdout TTY
```

## Color palette

- Header: bold (`\x1b[1m`)
- Alternating data row: dim (`\x1b[2m`)
- Threshold: red (`\x1b[31m`) when >100%, yellow (`\x1b[33m`) when >=80%

## Color opt-out

- `--no-color` flag on consuming commands (e.g. `isaac sessions --no-color`)
- `NO_COLOR` env var honored when non-empty (no-color.org convention)
- `--color always` to force color (useful for piping to `less -R`, tests)
- Default: autodetect TTY on stdout

## Spec

Two surfaces:

1. **Unit specs** in `spec/isaac/cli/table_spec.clj`:
   - renders headers and rows aligned to column widths
   - comma-formats numbers via a `:format` fn
   - applies bold to the header row when `:color?` is true
   - dims every other row when `:zebra?` is true
   - emits no ANSI escapes when `:color?` is false
   - applies `:color-fn` output to a cell
   - prints just the header row when there are no data rows
   - auto-detects color from TTY when `:color?` is not set
   - respects `NO_COLOR` env var even when `:color?` is true

2. **Integration scenarios** in `features/cli/sessions.feature` (committed @wip in b3ca9d6f):
   - sessions output is colorized when --color always is set
   - sessions --no-color suppresses ANSI escapes

## Implementation surfaces

- `src/isaac/cli/table.clj` — the renderer (new)
- `src/isaac/cli/color.clj` — small ANSI code helpers; `tty?` and `env`
  read seams so specs can stub them (new)
- `src/isaac/cli/sessions.clj` — replace ad-hoc formatting with `table/render`;
  accept `--color {auto,always,never}` and `--no-color` flags
- The existing "sessions output has aligned columns with a header row"
  scenario should be tightened: it uses `\s+` today which masks
  misalignment. Replace with explicit padding expectations.

## Definition of done

- All nine unit specs pass: `bb spec spec/isaac/cli/table_spec.clj`
- @wip tags removed from the two new sessions scenarios; they pass:
  `bb features features/cli/sessions.feature`
- The existing "aligned columns" scenario is tightened to verify
  alignment (not just whitespace presence) and still passes.
- `bb features` full suite stays green.
- Running `isaac sessions` in a real terminal produces colored,
  zebra-striped output that matches the sample from the planning chat.

## Related

- isaac-upve: `sessions show` / `sessions delete` subcommands +
  step rename. Independent work; this bean only touches the
  formatting layer and `sessions list`.
