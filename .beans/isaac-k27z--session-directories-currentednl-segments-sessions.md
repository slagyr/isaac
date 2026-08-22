---
# isaac-k27z
title: 'Session directories: current.ednl segments + sessions migrate'
status: todo
type: feature
priority: high
created_at: 2026-08-22T22:00:26Z
updated_at: 2026-08-22T23:41:59Z
---

Likely repo: **isaac-agent** (store, CLI, features). Foundation `fs/size`, `fs/copy`, `fs/read-bytes` are already required for bounded current-file I/O.

One bean on purpose (Micah 2026-08-22): implement locally as a single cutover, not a stack.

## Problem

Immortal sessions are one growing JSONL. Compaction still rewrites/copies the whole file. Byte offsets drift (isaac-63f3). Turns were supposed to see only the live view.

## Settled design (2026-08-22, Micah)

A session is a **directory**. Each compaction freezes the previous current file and starts a new one. Hot path never reads frozen segments. Transcript encoding is **EDNL** (one EDN map per line, `pr-str`), same in-memory keys (`:parentId`, `:type "message"`, …) — encoding change only, no transcript-key kebab.

```
sessions/<id>/
  session.edn     ; metadata. No :session-file, no :effective-history-offset
  0.ednl          ; frozen
  1.ednl          ; frozen
  current.ednl    ; the only file turns / append / compact / get-transcript / active-transcript touch
  turn.edn        ; was sessions/turns/<id>.edn
```

- **Compact `:retain`:** copy `current.ednl` → `{n}.ednl`, write new `current.ednl` (compaction entry + kept tail) via temp+rename, bump `:segment`. Crash order: copy first (old current still valid), then replace current.
- **Compact `:prune`:** skip the freeze; replace `current.ednl` only.
- **`get-transcript` / `active-transcript`:** `current.ednl` only. Offset is gone.
- **Whole chronicle** (episode migrate / rare ops): concatenate `0.ednl`…`n.ednl` then `current.ednl`.
- **Rename:** rename the directory. **Delete:** delete the directory.
- **One filesystem store.** Remove `:jsonl-edn-sidecar` and `:jsonl-edn-index`. Memory store stays for tests (in-memory vector is still the current view).
- **Dies:** `effective-history-offset`, `.bak.jsonl`, transcript `write-json`, dual file stores. Custom JSON writer exists only because cheshire is banned on native bb — `pr-str` is bb-safe.
- **Clean cutover.** Runtime never reads flat `.jsonl`. Opening one errors: run `isaac sessions migrate`. No lazy convert on turn.

### `isaac sessions migrate [session-id]`

Stays in the CLI until we scrap it later. Not called from the hot path.

- No id: migrate every leftover flat session under `sessions/`.
- With id: migrate that session only; missing / already-migrated / unknown id is a clean error or no-op (already-migrated = skip).
- Input: `sessions/<id>.jsonl` plus sidecar `sessions/<id>.edn` and/or `sessions/index.edn`.
- Split on compaction entries: spans before each compaction become `0.ednl`…`n.ednl`; tail after the last compaction (the live view) is `current.ednl`. Never compacted → everything is `current.ednl`.
- JSON objects → EDN keyword maps (cheshire `true` keys as they already are in memory).
- Idempotent: `sessions/<id>/current.ednl` exists → skip.
- Help lists `migrate`.

## Supersedes

- **isaac-962t** (jsonl vs ednl) — EDN won; scrap that draft.
- **isaac-xwwb** rotation at compaction boundaries — this *is* the rotation (every compact = new file). Gzip of frozen segments can stay a later draft; do not do it here.
- Local seek-into-giant-jsonl work is the right I/O for **`current.ednl` only**. Do not keep offset-into-a-68MB-file.

## Out of scope

- Renaming “session” → “chronicle”
- Kebab-casing transcript keys (`:parentId` stays)
- Compressing frozen segments
- Auto-migrate on boot or on turn

## Acceptance

Home: `isaac-agent`. Rewrite existing features that assume a flat `<id>.jsonl`; add migrate CLI scenarios. Marigold fixtures. Minimum new gherclj steps — reuse `isaac is run with`, session table steps, transcript matching. New steps only if an existing one cannot assert directory layout / `current.ednl` / frozen `{n}.ednl`.

1. **Create** writes `sessions/<id>/session.edn` and `sessions/<id>/current.ednl` (session header line). No sibling `<id>.jsonl` / `<id>.edn`.
2. **Append** only grows `current.ednl`.
3. **Compact `:retain`:** previous `current.ednl` is frozen as `{n}.ednl` (still contains compacted messages); new `current.ednl` is compaction + kept tail. Compact does not rewrite frozen files.
4. **Compact `:prune`:** no `{n}.ednl`; `current.ednl` is compaction + kept tail only.
5. **`get-transcript` / prompt path** read `current.ednl` only (frozen files unread).
6. **Rename** moves the directory; **delete** removes it.
7. **`isaac sessions migrate`** converts a flat jsonl+sidecar session (with at least one compaction) into the directory layout; jsonl is gone; `current.ednl` is the post-last-compaction tail; earlier spans are `{n}.ednl`.
8. **`isaac sessions migrate <id>`** converts only that session; a second session stays flat.
9. **`isaac sessions migrate`** with no args converts every leftover flat session; already-migrated dirs are skipped.
10. Opening / listing does not parse leftover `.jsonl`. Attempting to use an unmigrated flat session errors telling the operator to migrate (no silent convert).
11. `isaac sessions --help` / `sessions migrate --help` document the optional id.
12. `bb spec` store + compaction specs green; `bb features` for rewritten `features/session/storage.feature`, `features/session/history_retention.feature`, and new `features/session/migrate.feature`.

Runnable once features land (adjust LINE after writing):

```
cd isaac-agent && bb spec spec/isaac/session/store/
cd isaac-agent && bb features features/session/storage.feature
cd isaac-agent && bb features features/session/history_retention.feature
cd isaac-agent && bb features features/session/migrate.feature
```


## Decision (2026-08-22, Micah)

Transcript parse uses **tonsky/fast-edn** (`io.github.tonsky/fast-edn`) on the JVM — JSON-speed EDN, not clojure.edn (≈15× slower). Native bb has no `EdnParser`; `read-edn-line` / `read-ednl` fall back to `clojure.edn`. session.edn and turn markers stay `clojure.edn` (tiny).
