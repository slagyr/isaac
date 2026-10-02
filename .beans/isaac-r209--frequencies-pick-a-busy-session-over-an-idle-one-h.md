---
# isaac-r209
title: Frequencies pick a busy session over an idle one (hails pile onto the running worker)
status: completed
type: bug
priority: high
created_at: 2026-10-02T14:43:02Z
updated_at: 2026-10-02T17:04:59Z
---

Found 2026-10-02 on zanebot: three hails to band `isaac-work` (isaac-izc1, isaac-o13p, isaac-ixcm), sent seconds apart, all resolved to session `isaac-work-1`. The first started a turn; the other two arrived while it was busy and were folded into the running turn as extra messages (records `finished` immediately, `turn/waiting` → woke), while `isaac-work-2` and `isaac-work-3` (same crew/tags) sat idle. The planner had to re-hail them session-direct.

Micah: hail used to prefer idle sessions; this regressed when the turn queue moved into agent — frequencies now resolve to a session in `isaac.agent.frequencies` (match by session-tags/crew, tiebreak `:prefer :recent`), which ignores whether the session is busy.

## Wanted
When several sessions match a frequencies map, prefer a session with no running or queued turn; only when all are busy fall back to the existing tiebreak (and then queue, not merge into a running turn, for a separate request — confirm with Micah whether separate hails should ever coalesce).

## Acceptance (scenarios TBD)
- Two sessions match; one has a running turn → the turn goes to the idle one.
- All matching sessions busy → existing tiebreak, and the request waits its turn.

## Decision (Micah, 2026-10-02)

Separate hails queue; they never merge into a running turn.

## Root cause (planner, 2026-10-02, from code + zanebot log)

Agent does try to prefer idle sessions: the queue wake path (`turn/worker.clj` `wake-charge`) resolves a hail's frequencies with `busy = (store/in-flight-sessions ss)` and `frequencies.clj` drops busy matches before `pick-by-prefer`. But "in-flight" is the session store's in-memory `in-flight*` atom, which a session joins only when the drive accepts the turn — not when the queue claims a record for it. Since isaac-e9jl the tick claims a record and starts it on its own thread, so there is a window between claim and drive-accept where the chosen session still looks idle.

Zanebot timeline: izc1 hail 3e07a1fb claimed/started 14:33:32.618 on isaac-work-1; its `drive/turn-accepted` came at 14:33:33.141. Hail 9ab65a0a was resolved at 14:33:33.133 (inside the window) → isaac-work-1; 41d3f148 at 14:33:33.150 (just after accept) → isaac-work-1 too. Both then hit the busy session → `turn/waiting` → coalesced into the running turn (merge, not queue).

## Fix direction
- A session claimed by the queue counts as busy from the moment of the claim (reserve it in the same in-flight set, or have wake consult claimed/running records in the turn store), so the next resolution in the same tick or seconds later picks an idle match.
- Separate hails never merge into a running turn (decision above; isaac-e3f4 generalizes as a sender choice).

## Design (Micah + planner, 2026-10-02) — supersedes the fix directions above

Sessions are NOT resource pools (explored and rejected: sessions are identities, not interchangeable units; membership is derived from frequencies, can be empty and can grow via :create; a session "pool" object would hold no state of its own). What sessions and resource pools share is the admission step:

- **One synchronous admission step at claim time.** When agent's queue claims a charge it acquires, as one all-or-nothing step, a session from the charge's frequencies (a free matching session, by the usual :prefer) plus its resource-pool leases. If anything is busy it releases what it got and the charge waits in agent's queue. The chosen session is reserved from the moment of the claim (closing today's claim-to-accept race).
- **Membership is computed at admission time** from the frequencies (current sessions only).
- **Empty match never waits:** `:create :if-missing` creates a session; `:create :never` fails immediately with "no session matches".
- **All matching sessions busy:** the charge waits in agent's queue (held) and is retried when any session or pool lease is released (existing wake hook).
- **Hails never merge** into a running turn; chat bursts to one session keep coalescing (isaac-e3f4 later makes merge/queue a sender choice).
- Resource pools stay as they are (declared, interchangeable capacity); a future provider-token budget is one more pool in the same admission step.

## Acceptance (Micah approved 2026-10-02; gated)
- The 5 @wip scenarios in isaac-agent `features/turn/session_selection.feature` pass with @wip removed: two hails claimed in one tick land on different sessions; with every match busy a hail waits then runs as its own turn on the session that frees (never merged); `:create :never` empty match fails immediately; `:create :if-missing` creates; a free session is not reserved by a pool-busy charge. The planning commit added queue steps that drive agent's `submit!` like hail does.
- Guard (unchanged, must stay green): `features/session/waiting.feature` coalescing (isaac-2tez/xoqn).
- `bb ci` + jvm-spec/jvm-features green.

feature-baseline: isaac-agent ea52185597e904e54daf6b092eab5f8f62d316f7
feature-blob: isaac-agent features/turn/session_selection.feature 868cf43e23e0e5604b74690e30271869c83f3976 83,111,137,143,160

## Landed on main (2026-10-02)

main-sha: isaac-agent 085decd82a0e9255ebf8b47a62489e4da058e16e
