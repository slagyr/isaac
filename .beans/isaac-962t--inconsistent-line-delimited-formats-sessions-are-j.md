---
# isaac-962t
title: 'Inconsistent line-delimited formats: sessions are JSONL, logs are EDNL'
status: draft
type: task
priority: normal
created_at: 2026-05-23T02:49:10Z
updated_at: 2026-05-23T02:49:10Z
---

## Gap

Isaac uses two different line-delimited formats for on-disk records:

- **Sessions** — `~/.isaac/sessions/<name>.jsonl` — one JSON object per
  line.
- **Logs** — line-delimited EDN (`(pr-str entry) "\n"` in
  `isaac.logger`).

There's no functional reason for the split. Tools that read one can't
read the other. Operators have to remember which is which. Internal
consistency principle is the issue.

## Decision needed

Pick one format and migrate the other. Two reasonable choices:

1. **EDN everywhere.** Pros: idiomatic in Clojure, preserves keywords,
   sets, namespaced keys, instants, etc. without quoting. Cons:
   external readers (jq, browser, anything non-Clojure) lose easy
   access. Sessions currently include some Clojure-native values
   (keywords, etc.) that JSON has to flatten/stringify.
2. **JSON everywhere.** Pros: universal tooling (`jq`, browsers, log
   shippers). Cons: lossy round-trip for Clojure values; need a
   convention for keywords/namespaced keys; instants need ISO strings.

I'd lean **EDN everywhere** — Isaac is a Clojure system end-to-end, and
the log viewer (`isaac logs`) already parses EDN. The lift is mostly
swapping the session writer to `pr-str` and updating any session
readers that today expect JSON.

But this is a real call to make before any work starts; flagging as the
key decision rather than committing.

## Surface (sketch, pending decision)

If EDN wins:
- Session writer in `isaac.session.store.file` (or wherever sessions
  are serialized): swap `json/generate-string` → `pr-str`.
- Session readers: swap parser; tag-handler for `#inst`, keywords, etc.
- File extension migration: `.jsonl` → `.ednl` (or keep `.jsonl` and
  just have it contain EDN — confusing; prefer renaming).
- Backwards compat: if any existing session files matter, a read-side
  fallback or a one-shot conversion.

If JSON wins:
- Log writer in `isaac.logger`: swap `pr-str` → `json/generate-string`
  with a convention for keywords/namespaced keys (`:foo/bar` →
  `"foo/bar"`?) and instants.
- `isaac logs` viewer parser swap.

## Acceptance

- One line-delimited format used across sessions and logs.
- File extensions match the format consistently.
- Existing tooling (log viewer, session search if any) still works
  against the unified format.

## Origin

Surfaced during a sanity check of the two formats while discussing
on-disk conventions.

