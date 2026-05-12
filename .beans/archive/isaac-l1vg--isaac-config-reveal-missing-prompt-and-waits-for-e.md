---
# isaac-l1vg
title: "isaac config --reveal: missing prompt and waits for EOF instead of newline"
status: completed
type: bug
priority: low
created_at: 2026-04-18T23:07:12Z
updated_at: 2026-04-19T02:17:44Z
---

## Description

Two bugs in `reveal-confirmed?` (src/isaac/cli/config.clj:50-54):

1. **No prompt printed.** Interactive users see a blank terminal with no indication that input is expected. The command should print "type REVEAL to confirm: " to stderr before reading.

2. **Waits for EOF instead of newline.** Current implementation uses `(str/trim (slurp *in*))`, which reads until stdin closes (Ctrl+D). Should use `read-line` so a plain Enter keypress after typing REVEAL works.

Affects both `isaac config --reveal` and `isaac config get <path> --reveal` (both call `reveal-confirmed?`).

## Repro
```
$ isaac config --reveal
<cursor hangs with no prompt; Ctrl+D needed to end input>
```

## Proposed fix
```clojure
(defn- reveal-confirmed? []
  (binding [*out* *err*]
    (print "type REVEAL to confirm: ")
    (flush))
  (= "REVEAL" (some-> (read-line) str/trim)))
```

- Prompt → stderr keeps stdout clean for pipes
- `read-line` returns on newline, no EOF required

## Scenario gap
features/cli/config.feature "config --reveal shows real values after typed confirmation" asserts the output contains the secret but doesn't assert the prompt was printed. Add `And the stderr contains "type REVEAL"` to both the config-reveal and get-reveal happy scenarios. The refusal scenarios already check stderr for this string.

## Acceptance Criteria

1. isaac config --reveal prints 'type REVEAL to confirm: ' to stderr before reading
2. A newline after typing REVEAL completes the confirmation (no Ctrl+D needed)
3. isaac config get <path> --reveal has the same fix (shared code path)
4. Happy-path scenarios in features/cli/config.feature add a stderr assertion for the prompt
5. bb features passes
6. bb spec passes

