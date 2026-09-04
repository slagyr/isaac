---
# isaac-vrtb
title: Block broken conversations; compaction-failed is the first reason
status: draft
type: bug
priority: high
created_at: 2026-08-31T14:15:35Z
updated_at: 2026-09-04T16:53:13Z
---

Likely repo: **isaac-agent** (session schema, drive turn gate, attention). **isaac-hail**: stop special-casing `:context-exhausted` (generic `:unavailable?` + `:retry-after-ms` only). Comm protocol: drop `on-compaction-disabled` / `:compaction/disabled` (isaac-server, isaac-discord, isaac-acp, isaac-imessage).

## Problem

`:compaction-disabled` turns off compaction and then treats every later turn as window weather. Hail retries every 5 minutes into a session that will never compact. That is the expensive failure. Compaction is necessary; the *conversation* is what should stop, not the compact capability.

Failed compaction is one reason a conversation needs intervention. There will be others. Self-repair is the ideal; until then, a broken conversation must not accept turns.

## Decisions (2026-09-04, Micah)

1. **General block, not a compaction switch.** Replace `:compaction-disabled` with a session `:block` map (`{:reason :keyword :at iso}`). Nil/absent = healthy. First producer: `:reason :compaction-failed` after **3** consecutive compact failures (`max-compaction-attempts` 5→3). Other reasons can write the same field later. Clean cutover: drop `:compaction-disabled`; unknown keys already drop on read.
2. **Blocked conversations refuse turns.** Drive returns `{:unavailable? true :reason :blocked}` before appending the user/assistant and before the user LLM. The chronicle does not grow. Revive is explicit: `isaac sessions unset <id>.block` (or equivalent). Compaction remains a capability — it runs again on the first turn after unblock.
3. **Intervention, not a 5-minute hammer.** Attention posts once when the block is set (session, reason, tokens, window) from **agent**, not hail. Copy is "Conversation blocked …", never "compaction disabled". Drive returns `{:unavailable? true :reason :blocked :retry-after-ms …}`; hail parks like any other unavailable (hails-never-die).
4. **This-turn compact failure (before the 3rd).** If compact was required and failed, do not take the user turn (no assistant row). Count the failure. At 3, set `:block`. p9zy overflow compact-and-retry still applies when the conversation is *not* blocked.
5. **Protocol.** Remove `on-compaction-disabled` and bulletin `:compaction/disabled`. Keep `:compaction/failure`.
6. **Hail does not know context.** Delete `isaac.hail.attention/maybe-notify-context-exhausted!` and the `when (= :context-exhausted reason)` branch in `defer-delivery!`. Hail already defers on `:unavailable?` + `:retry-after-ms`; `:reason` may be logged but must not select attention copy. `:auth` attention stays. Rewrite/delete hail `features/context_window_guard.feature` rows that assert context-exhausted attention.

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

Hail `features/context_window_guard.feature`: drop context-exhausted attention assertions; unavailable deferral stays generic (`delivery.feature` already covers attempt-free defer).

## Out of scope

- Auto-unblock / compact-only housekeeping (self-repair).
- A catalog of other `:block` reasons.
- Changing the 0.8 compact threshold or p9zy's overflow retry for *unblocked* sessions.

## Scenario plan (2026-09-04, rev 2)

1. First required compact failure refuses the user turn — consecutive-failures 1, no `:block` — **approved**; `@wip` in `features/session/context_window_guard.feature`
2. Third consecutive compact failure sets `:block {:reason :compaction-failed}` and posts attention once — **approved**; `@wip` in `features/session/context_window_guard.feature`
3. A blocked conversation refuses the next turn — no LLM, no assistant row, reason `:blocked` — **approved**; `@wip` in `features/session/context_window_guard.feature`
4. Unset `:block` — the next needing turn is accepted (compaction can run) — **approved**; `@wip` in `features/session/context_window_guard.feature`
5. Hail defers an unavailable turn without posting context-exhausted attention

## Acceptance

Draft until `@wip` scenarios exist.
