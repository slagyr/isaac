---
# isaac-2o68
title: crew list table is broken — multi-line soul cells and bespoke renderer
status: unverified
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-06-22T21:14:57Z
updated_at: 2026-06-22T21:40:00Z
---

`isaac crew list` renders a mangled table: the Soul column contains the crew's full multi-line soul markdown (starts with `# SOUL.md — X\n\nYou are…`), so embedded newlines land inside a cell and shatter every row. It also hand-rolls its own table instead of the shared renderer used elsewhere.

## Two fixes (both in isaac-agent `src/isaac/crew/cli.clj`)

### 1. Use the standard table renderer
`format-crew` (~lines 68-79) hand-rolls padding/rules. Replace with `isaac.cli.table/render` — the same renderer `isaac.session.cli` uses, which gives aligned columns, zebra striping, color, and TTY-aware width/truncation:
```clojure
(table/render {:columns [{:key :name        :header "Name"     :align :left}
                         {:key :model       :header "Model"    :align :left}
                         {:key :provider    :header "Provider" :align :left}
                         {:key :soul-source :header "Soul"     :align :left}
                         {:key :tags-text   :header "Tags"     :align :left}]
               :rows    rows
               :zebra?  true
               :color?  color?})
```
(Column spec format per session-columns: `{:key :header :align :format :color-fn}`.)

### 2. Strip/collapse soul newlines
`soul-source` (~lines 33-37) truncates to 40 chars but keeps embedded newlines. Collapse all whitespace to single spaces BEFORE truncating so the cell is single-line, e.g.:
```clojure
(defn- soul-source [crew-cfg]
  (when-let [s (:soul crew-cfg)]
    (let [oneline (-> s (str/replace #"\s+" " ") str/trim)]
      (if (> (count oneline) 40) (str (subs oneline 0 37) "...") oneline))))
```
Consider also stripping a leading markdown heading (`# SOUL.md — X`) so the preview shows actual soul text, not the filename header. (Optional — confirm desired snippet.)

## Acceptance
- `isaac crew list` renders a single clean, aligned table (no row-shattering); each row is one line.
- Soul column is a single-line snippet (no newlines), truncated with an ellipsis.
- Styling matches `session list` (shared `isaac.cli.table`): zebra, color, TTY-aware widths.
- `--json`/`--edn` output paths unchanged.
- Update the crew cli spec to cover a multi-line soul collapsing to one line.

## Notes
Surfaced 2026-06-22 on zanebot after the agent deploy unblocked `crew list`. The single-row `crew show` path (`render-row!`) uses the same `format-crew` — fix benefits both.
Implemented in isaac-agent @ a8c143f.
