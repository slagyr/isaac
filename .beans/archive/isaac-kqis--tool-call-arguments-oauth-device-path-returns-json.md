---
# isaac-kqis
title: "Tool call arguments: oauth-device path returns JSON string, breaks notifications"
status: completed
type: bug
priority: normal
created_at: 2026-04-28T15:37:50Z
updated_at: 2026-04-28T16:09:28Z
---

## Description

Marvin (openai-codex / oauth-device) crashes on every tool call with:

  :event :acp/turn-error
  :ex-class "ClassCastException"
  :error-message "java.lang.Character cannot be cast to java.util.Map\$Entry"

No :tool/start logs — the crash fires in the notification layer
*before* tool execution.

## Root cause

src/isaac/llm/openai_compat.clj produces two different :arguments
shapes depending on auth method:

  Line 273-277, chat-completions path (api-key providers):
    :tool_calls (mapv (fn [tc] {:function {:arguments (:arguments tc)}}) ...)
    -> :arguments is parsed Clojure data (a map)

  Line 295-296, /responses path (oauth-device = openai-codex):
    :function {:arguments (json/generate-string (:arguments tc))}
    -> :arguments is a JSON-encoded string

This asymmetry leaks downstream:

- src/isaac/drive/turn.clj:230 tool-loop-call reads
  (get-in raw-tool [:function :arguments]) raw, no parse.
- src/isaac/comm/acp.clj:33-40 tool-title does
  (first (vals arguments)) -> (vals "{...}") iterates the string
  as a char-seq, treats each Character as Map\$Entry, crashes.
- turn.clj:517 (assoc arguments :session-key ...) would crash
  too if execution got that far.

## Fix

Drop json/generate-string at openai_compat.clj:296 — return parsed
arguments like the chat-completions path. Re-serialize ONLY at the
seam where we echo tool_calls back into the next API request's
messages (turn.clj `assistant-tool-loop-message` and
openai_compat.clj's chat-with-tools/non-streaming/streaming
re-emit sites — multiple lines need json/generate-string put
back in just for the wire).

Audit checklist for re-serialize sites:
  openai_compat.clj:274-277 (chat-completions response shape — already symmetric internally)
  openai_compat.clj:296     (/responses response shape — change here)
  openai_compat.clj:340     (chat-with-tools assistant-msg)
  openai_compat.clj:367     (chat-with-tools assistant-msg)
  turn.clj:242              (assistant-tool-loop-message — already serializes if string?)

After the fix, downstream code (turn.clj, comm/acp.clj) always sees
:arguments as a Clojure map. No defensive parsing needed at the
notification or execution sites.

## Definition of done

- Marvin can call tools on zanebot without ClassCastException.
- A new spec exercises a /responses-path tool call returning args
  as a Clojure map at the public chat seam.
- bb features and bb spec green.

