---
# isaac-g1bh
title: "isaac config output is one-line EDN; should be pretty-printed"
status: completed
type: bug
priority: low
created_at: 2026-04-18T15:04:23Z
updated_at: 2026-04-18T15:15:46Z
---

## Description

`isaac config`, `isaac config --raw`, `isaac config --reveal`, and `isaac config get <path>` all print using `(println (pr-str ...))` which produces a single-line EDN dump. The command is for human reading; multi-line pretty-print is expected.

Source: src/isaac/cli/config.clj lines 139, 149, 164, 216 — all use pr-str. Should delegate to clojure.pprint/pprint (or equivalent).

The existing feature scenarios in features/cli/config.feature use regex matches that pass against either one-liner or multi-line, so this slipped through isaac-kh5s without catching the regression.

## Fix scope
1. Swap pr-str → pprint in the four print sites
2. Add a scenario asserting multi-line output so this doesn't silently regress
3. May require a new step 'the output has at least N lines' if it doesn't already exist

## Repro
```
$ isaac config
{:providers {"ollama" {:api "ollama", :baseUrl "http://localhost:11434"}}, :crew {"main" {}}, :defaults {:crew :main, :model :llama}, :models {"llama" {:contextWindow 32768, :provider :ollama, :model :llama3.3:1b}}}
```

## Acceptance Criteria

1. isaac config prints multi-line (at least 5 lines for a non-trivial config)
2. isaac config --raw prints multi-line
3. isaac config --reveal prints multi-line
4. isaac config get <path> on a nested value prints multi-line
5. Add at least one feature scenario asserting multi-line output
6. bb features features/cli/config.feature passes
7. bb spec passes

