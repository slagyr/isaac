---
# isaac-q3uk
title: sessions list SIZE must stat the transcript file, not parse it
status: in-progress
type: bug
priority: high
created_at: 2026-08-18T13:48:47Z
updated_at: 2026-08-18T15:06:12Z
---

## Goal

`isaac sessions list` hangs on zanebot (~5 min) because SIZE slurp-parses and re-serializes every transcript. We want **on-disk file size** (`ls -l`), not a reconstructed JSON byte count.

Observed 2026-08-18: 1.3G under `~/.isaac/sessions`, `isaac-work-1.jsonl` 41MB, `tono-work-1.jsonl` 36MB. List eventually prints; it is not deadlocked.

## Settled design (2026-08-18, Micah)

- SIZE = `fs/size` of `sessions/<session-file>`. Missing file → 0.
- Never call `get-transcript` / `migrate-transcript!` / `transcript-byte-offset` on the list path.
- Leave `transcript-byte-offset` alone — it is the compaction offset helper, not a table metric.
- Add `fs/size` on the existing `Fs` protocol (only RealFs + MemFs, same file):
  - RealFs: `File.length` (O(1), no content I/O)
  - MemFs: UTF-8 byte length of the stored string (features stay hermetic)
- Do **not** slurp-and-count on RealFs as a shortcut.

## Home

- `isaac-foundation` — `fs/size`
- `isaac-agent` — `isaac.session.cli/transcript-size-bytes`; bump foundation pin

## Acceptance

Existing scenario (expected output unchanged):

```
cd isaac-agent && bb features features/session/cli.feature:237
```

Also:

```
cd isaac-agent && bb spec spec/isaac/session/cli_spec.clj
cd isaac-foundation && bb spec spec/isaac/fs_spec.clj
```

DoD:

- [ ] `fs/size` on RealFs + MemFs; missing path → nil or 0 (pick one, use it)
- [ ] list SIZE uses `:session-file` + `transcript-path` + `fs/size`
- [ ] spec: listing does **not** invoke `get-transcript` (redef/spy)
- [ ] existing SIZE scenario still green (`compact-chat` `\d+B`, `roomy-chat` `\d+(\.\d)?K`)
- [ ] `transcript-byte-offset` callers (compaction) unchanged

## Worker notes

- Current smell: `cli.clj` `transcript-size-bytes` → `store/get-transcript` → `transcript-byte-offset` (parse + re-serialize every line).
- `isaac sessions list --json` already skips SIZE; table path is the bug.
- Follow-up not in scope: `isaac session` (singular) is not a command.
