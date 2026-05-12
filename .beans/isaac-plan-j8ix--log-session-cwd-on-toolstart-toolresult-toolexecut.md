---
# isaac-plan-j8ix
title: Log session cwd on :tool/start, :tool/result, :tool/execute-failed
status: todo
type: feature
created_at: 2026-05-12T22:43:20Z
updated_at: 2026-05-12T22:43:20Z
---

## Problem

Tool log entries currently print arguments but not the session cwd that
relative paths resolve against. Example from production:

    DEBUG  :tool/start  {:arguments {:file_path "spec/isaac/server/routes_spec.clj"
                                     :offset 1 :limit 40
                                     :session_key "tidy-comet"}
                        :tool "read"}

`spec/isaac/server/routes_spec.clj` is ambiguous without knowing which
project the session is running in. Surfacing `:cwd` next to the tool
event makes log archaeology workable.

## Change

In `src/isaac/tool/registry.clj` `run-handler` (~line 85), resolve the
session cwd from the runtime-injected `session_key` and add it to each
of the three tool events:

- `:tool/start` — `:cwd` field, alongside `:tool` and `:arguments`.
- `:tool/result` — `:cwd` field, alongside `:tool` and result metadata.
- `:tool/execute-failed` — `:cwd` field, alongside `:tool` `:arguments` `:error`.

Source the cwd via `isaac.tool.fs-bounds/session-workdir`, which already
encapsulates "get the session entry's :cwd from the session store." It
takes a session key string and returns the cwd or nil. When `session_key`
is absent (utility tools that don't take one), log `:cwd nil` — explicit
nil is more useful than a missing field for grep/jq pipelines.

Drop the redundant `:session_key` field from the logged arguments map
once `:cwd` is present — the cwd is the useful signal, the key is just
noise repeated on every line. Implement this in `log-arguments` by
dissoc-ing `"session_key"` and `:session_key` after coercing keys.

## Test plan

`spec/isaac/tool/registry_spec.clj` already exercises `:tool/start`,
`:tool/result`, and `:tool/execute-failed` (lines 189–229). Extend it:

- Assert `:cwd` is present on all three events for a tool invocation
  that includes `session_key`. Use a real test session with a known
  cwd (the spec helper sets one up).
- Assert `:cwd` is `nil` on a tool invocation without `session_key`.
- Assert `:session_key` is *not* present in the `:arguments` map of
  the start event (and is absent from `:arguments` in execute-failed
  too).

## Out of scope

- Changing the log level (stays `:debug` for start/result, `:error` for
  execute-failed).
- Adding cwd anywhere else (e.g., `:chat/message-stored`). If that's
  desired later, separate bean.

## Definition of done

- [ ] All three events carry `:cwd`.
- [ ] `:session_key` no longer appears in logged `:arguments`.
- [ ] registry_spec covers the new fields; `bb spec spec/isaac/tool/registry_spec.clj` green.
- [ ] `bb spec` green overall.
- [ ] `bb features` green overall.
