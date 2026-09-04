---
# isaac-vrtb
title: Block broken conversations; compaction-failed is the first reason
status: draft
type: bug
priority: high
created_at: 2026-08-31T14:15:35Z
updated_at: 2026-09-04T16:53:13Z
---

Likely repo: **isaac-agent** (session schema, drive turn gate, attention). Hail must learn the new refusal so it does not 5-minute-retry a blocked conversation as `:context-exhausted` weather. Comm protocol: drop `on-compaction-disabled` / `:compaction/disabled` (isaac-server, isaac-discord, isaac-acp, isaac-imessage).

## Problem

`:compaction-disabled` turns off compaction and then treats every later turn as window weather. Hail retries every 5 minutes into a session that will never compact. That is the expensive failure. Compaction is necessary; the *conversation* is what should stop, not the compact capability.

Failed compaction is one reason a conversation needs intervention. There will be others. Self-repair is the ideal; until then, a broken conversation must not accept turns.

## Decisions (2026-09-04, Micah)

1. **General block, not a compaction switch.** Replace `:compaction-disabled` with a session `:block` map (`{:reason :keyword :at iso}`). Nil/absent = healthy. First producer: `:reason :compaction-failed` after **3** consecutive compact failures (`max-compaction-attempts` 5→3). Other reasons can write the same field later. Clean cutover: drop `:compaction-disabled`; unknown keys already drop on read.
2. **Blocked conversations refuse turns.** Drive returns `{:unavailable? true :reason :blocked}` before appending the user/assistant and before the user LLM. The chronicle does not grow. Revive is explicit: `isaac sessions unset <id>.block` (or equivalent). Compaction remains a capability — it runs again on the first turn after unblock.
3. **Intervention, not a 5-minute hammer.** Attention posts once when the block is set (session, reason, tokens, window). Copy is "Conversation blocked …", never "compaction disabled". Hail must **not** classify `:blocked` as `:context-exhausted` weather (isaac-dark / isaac-bs5b). Park the hail until the block is cleared (hails-never-die); do not spin `retry-after-ms` 300000 into a session that needs a human.
4. **This-turn compact failure (before the 3rd).** If compact was required and failed, do not take the user turn (no assistant row). Count the failure. At 3, set `:block`. p9zy overflow compact-and-retry still applies when the conversation is *not* blocked.
5. **Protocol.** Remove `on-compaction-disabled` and bulletin `:compaction/disabled`. Keep `:compaction/failure`.

## Not yet other reasons

Do not inventory a taxonomy in this bean. The field is open; compaction-failed is the first writer. Turnstile `:held` stays a different thing (temporary park, not broken).

## Existing scenarios

| file | verdict |
|------|---------|
| `context_window_guard` "compaction disabled over the guard line…" | **rewrite** — blocked conversation refuses the turn (no LLM); reason `:blocked` not `:context-exhausted` |
| `context_window_guard` "compaction failure cap posts attention…" | **rewrite** — 3rd failure sets `:block`, attention posts once |
| `compaction_logging` "stops retrying after max consecutive…" | **rewrite** — block + refuse; compaction is not skipped as a capability |
| `compaction_logging` "switching model clears compaction-disabled" | **delete** — revive is unset `:block`, not a model swap |
| `compaction_logging` "Compaction failure is logged and chat proceeds" | **rewrite** — required compact failed → this turn refused, no assistant row |
| `compaction_overflow` "prompt-too-long with compaction disabled…" | **rewrite** — blocked vs this-turn overflow are different; do not seed the old flag |

Hail `features/context_window_guard.feature`: blocked session does not 5-minute-defer as context-exhausted; hail stays parked until unset.

## Out of scope

- Auto-unblock / compact-only housekeeping (self-repair).
- A catalog of other `:block` reasons.
- Changing the 0.8 compact threshold or p9zy's overflow retry for *unblocked* sessions.

## Acceptance

Draft until `@wip` scenarios exist.
