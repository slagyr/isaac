---
# isaac-qvjj
title: 'Sessions/status: USED shows cumulative billing instead of last-turn context size'
status: completed
type: bug
priority: normal
created_at: 2026-05-18T20:02:56Z
updated_at: 2026-05-18T20:15:39Z
---

## Problem

`isaac sessions` and the bridge `/status` slash command both display
context-window usage that's actually cumulative input+output across
every turn ever. A long-running session shows numbers like
`23,637,042 / 278,528 = 8486%` even when the most recent prompt was
only 5K tokens.

Observed on zanebot:

```
% isaac sessions --crew marvin
SESSION                      AGE        USED   WINDOW    PCT
tidy-comet                    3h  23,637,042  278,528  8486%
discord-1471622305510723745   5d     294,272  278,528   106%
```

The sidecar for tidy-comet shows `:last-input-tokens 5339` (1.9%) —
compaction is working correctly. The display is just reading the wrong
field.

## Root cause

Both display layers read `:total-tokens` from the session entry, which
is the running sum of input+output tokens billed across every turn.
Per-turn billing is fine for cost tracking, but it's not what USED/PCT
mean — those mean "how full is the model's context window for the next
prompt." That's `:last-input-tokens` (or its alias on the entry).

Affected files:

- `src/isaac/session/cli.clj:76` — `(or (:total-tokens entry) 0)` →
  must use `:last-input-tokens`
- `src/isaac/bridge/status.clj:62` — same fix in `status-data`

The model code itself already uses `:last-input-tokens` correctly
(see `src/isaac/drive/turn.clj:511`), so this is purely a presentation
bug.

## Decision (confirmed)

USED means current context-window usage; if USED == WINDOW then
PCT = 100%. Do **not** rename the underlying `:total-tokens` field;
just stop reading it for USED/PCT/Context displays.

## Spec

Two @wip scenarios committed in d71fa617:

- `features/cli/sessions.feature`: \"USED shows last-turn context size,
  not cumulative billing\" — asserts a session with
  `:total-tokens 1000000 :last-input-tokens 5000` displays \"5,000\"
  in the USED column and never \"1,000,000\".
- `features/bridge/commands.feature`: \"/status Context shows last-turn
  size, not cumulative billing\" — same shape against `/status` output.

## Implementation surfaces

1. `src/isaac/session/cli.clj` — swap `:total-tokens` → `:last-input-tokens`
   in `session->row` and the column-format helper.
2. `src/isaac/bridge/status.clj` — swap in `status-data`.
3. **Migrate existing scenarios that test the displayed value**:
   - `features/cli/sessions.feature` \"sessions output has aligned
     columns with a header row\" — Background sets `:total-tokens 5000`;
     change to `:last-input-tokens 5000`.
   - `features/bridge/commands.feature` Background — same; update the
     \"Context .* 5,000 / 32,768 .*15%\" assertion against the new
     setup.
   - Any other scenario that asserts on `Context` / `USED` token values.
4. **Audit**: grep for `:total-tokens` across `src/` and `spec/` to
   catch any other display-layer misuse. Production code that wants
   cumulative billing (cost reporting, future) keeps reading
   `:total-tokens`; only display surfaces change.

## Definition of done

- Both @wip scenarios pass; their `@wip` tags removed.
- Existing sessions/status scenarios still pass after their setups
  migrate to `:last-input-tokens`.
- `bb features` full suite stays green.
- `bb spec` stays green.
- A live `isaac sessions --crew marvin` against tidy-comet shows
  approximately the actual context usage (a small percentage, not
  thousands of percent).

## Verification commands

- `bb features features/cli/sessions.feature`
- `bb features features/bridge/commands.feature`
- `bb features`
- `bb spec`
