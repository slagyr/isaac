---
# isaac-plan-8gb4
title: Rework isaac logs viewer UX (default limit, --plain, map payload, full-row zebra, --no-color, --follow)
status: todo
type: feature
created_at: 2026-05-12T20:41:04Z
updated_at: 2026-05-12T20:41:04Z
---

## Feature

Implements `features/cli/logs.feature` (currently `@wip`).

## Scope of changes

**`src/isaac/log_viewer.clj`**
- Expand `palette` to ~12 256-color entries so different namespaces visibly diverge (the current 6-color palette collides most events on magenta).
- `format-entry`: render the trailing payload as a Clojure map literal `{:k v :k v}` instead of `k=v k=v` (no commas, like clojure prn). Key uses `pr-str` so the leading colon is preserved.
- New `format-map` helper produces `{...}`; existing `format-kv` becomes the per-pair renderer in map context (`:k v`, not `k=v`).
- `zebra-wrap` helper that re-applies `bg-zebra` after every internal `reset` so the bg covers the whole row (not just the timestamp). Wrap odd rows in `bg-zebra + (string-replace reset → reset+bg-zebra) + reset`.
- `tail!` signature gains `:plain?` and `:limit`. `:plain?` skips parsing/coloring/zebra and prints raw lines. `:limit` reads only the last N entries from the existing file before optionally entering follow.
- Zebra row counter only increments on lines actually printed (skips blanks) so alternation stays consistent.

**`src/isaac/logs/cli.clj`**
- Replace `--no-follow` with `-f/--follow` (read-and-exit is now the default).
- Replace `--color MODE` with `--no-color` flag (color defaults on regardless of TTY).
- Replace `--zebra` with `--no-zebra` (zebra defaults on).
- Add `--limit N` (default 20, 0 = unlimited; `:parse-fn #(Long/parseLong %)`).
- Add `--plain` flag.
- Plumb new opts to `viewer/tail!`.

**`src/isaac/server/cli.clj`**
- Replace `--color MODE` with `--no-color` to mirror the logs subcommand.
- `start-log-tail!` calls `viewer/tail!` with `:limit 10` so the initial server prelude shows only the most recent 10 entries before streaming live ones.

**`spec/isaac/log_viewer_spec.clj`**
- Update `format-entry` expectations: substring should be `{:port 8080}` (map literal) instead of `port=8080`.
- Update zebra-row test to assert `bg-zebra` appears multiple times in the formatted line (proof that internal resets were patched), not just once at the start.
- Add a palette-distribution test: hash 20 distinct namespace strings, assert at least N distinct colors come out (sanity check that we're not collapsing onto one slot).
- Existing `color-for-ns`/`color-for-session` tests update to the new 256-color ANSI form (`38;5;NN`).

**New step phrases — `spec/isaac/features/helpers/cli.clj` + `steps/cli.clj` (or tools.clj alongside `file-with-lines`):**
- `(defgiven #"a file \"([^\"]+)\" exists with (\d+) log entries" tools/file-with-log-entries)` — writes N EDN log lines `{:ts ... :level :info :event :eNN}` (2-digit padded so substrings don't collide).
- `(defgiven #"the isaac file \"([^\"]+)\" exists with (\d+) log entries" server/isaac-file-with-log-entries)` — same, resolved against state-dir.

## Definition of done

- [ ] All scenarios in `features/cli/logs.feature` pass; remove the `@wip` tag.
- [ ] `bb spec` green.
- [ ] `bb features features/cli/logs.feature` green with assertions > 0.
- [ ] No new pending scenarios.
- [ ] Eyeball `isaac logs` against a live `~/.isaac/isaac.log`: zebra spans the full row, events of different namespaces use noticeably different colors, default limit is 20.
