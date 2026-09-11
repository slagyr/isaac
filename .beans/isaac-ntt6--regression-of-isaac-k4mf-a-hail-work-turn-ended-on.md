---
# isaac-ntt6
title: 'Exhausted turns: stop reason, exhaustion policy at the loop seam, bounded continuations, commit-on-green'
status: completed
type: feature
priority: high
created_at: 2026-09-08T13:34:08Z
updated_at: 2026-09-10T10:43:50Z
---

Repo: isaac-hail (delivery_worker) / isaac-agent (drive/turn). Reopens the isaac-k4mf contract: a hail-driven work turn must not silently complete on an empty terminal model response.

## Evidence (zanebot, agent 0.1.49 / hail 2d7cb55, 2026-09-07 01:27Z)
Hail 567b453a (tono-2fe1) on tono-work-1: turn 2026-09-06 20:08:50Z → 2026-09-07 01:27:09Z, `:turn/model-response-summary :tool-calls-count 1258 :executed-tools-count 1258 :assistant-content-chars 0 :error nil :provider grok`, then `:hail/turn-ended :outcome :delivered` and `:hail/delivered`. No handoff, no commit, no bean note; the bean stayed in-progress with 19 files (+1344/−626) uncommitted on the cochlea checkout's main branch for 30 h until a human looked. Same shape as isaac-vrtb on 2026-09-05 (one work turn, no handoff, 17 h idle).

## Why k4mf's guard did not fire (to confirm)
k4mf's investigation covered turns that ended with NO tool calls and empty content. This turn executed 1258 tools and then the model returned nothing — likely the crew's tool-loop-max (scrapper: 400) or the provider's own loop budget cutting the loop, with the drive treating the empty tail as a normal completion. The delivery worker must treat 'empty final content on a work-band turn' as :no-progress → re-bind for a continuation turn (not delivered, not dead-letter), and log `:hail/turn-no-handoff` with the executed-tools count.

## Scenarios (isaac-hail features/delivery.feature, @wip, to be drafted with Micah)
1. a work-band turn whose final response is empty after N tool calls is not delivered: the worker re-hails a continuation on the same session and logs :hail/turn-no-handoff
2. a work-band turn that ends with a handoff (bean tagged unverified) is delivered as today



## Mechanism — HYPOTHESIS, not confirmed (planner read of drive/turn.clj on main, 2026-09-08)
Checked after writing this: the canned loop-limit text appears in neither the session transcript nor the delivered hail record, and `assistant-content-chars 0` was logged. So either the canned message is applied to the result but never persisted, or the empty reply reached the delivery worker by a path that bypasses `guard-empty-terminal-response` (e.g. the provider-driven or parallel-batch loop returning without a terminal message). The worker must trace which. What IS established: 1258 tools, empty final content, :error nil, outcome :delivered.
k4mf's guard (`guard-empty-terminal-response`) does nudge once and then errors (`:empty-terminal-response`) — so a plain empty reply would NOT be delivered. The hole is the loop-limit path: `canned-loop-exhausted-message` replaces a blank terminal reply on a `:loop-request?` result with the canned text 'I ran several tools but did not reach a conclusion before hitting the tool loop limit. Ask me to continue…', which makes the content non-blank, so the turn completes normally and the delivery worker marks the hail :delivered. For an interactive chat that is the right UX; for a work-band hail there is no human to 'ask me to continue' — the bean silently stalls. Fix belongs in the delivery worker: when the terminal result is loop-exhausted (or the canned message) on a work/verify band, treat it as :continuation-needed → re-bind a continuation turn on the same session (bounded, e.g. 3 continuations per delivery), log `:hail/turn-continued :loops N`, and only then dead-letter-with-attention. Crew scrapper `tool-loop-max 400`.



## Decisions (2026-09-08, Micah — planning session)

Context: the 2fe1 turn ran to scrapper's 400-cycle cap (401 compaction checks, 514 model requests, 8 mid-turn compactions, 1258 tool calls) and ended with empty content marked :delivered; vrtb stopped the same way on 09-05. Industry survey: every autonomous runner reports budget exhaustion as a distinct outcome (OpenAI MaxTurnsExceeded, LangGraph recursion limit, SWE-agent auto-submit at cost limit); none marks it success. Terminology stays: cycle = one trip through the tool loop, turn = prompt → reply.

1. **Exhaustion policy is an abstraction at the loop seam.** The tool loop gains an on-exhausted hook (alongside the 1sdl hooks: cycle start/end, after-tools, cancelled?, max loops). The loop never knows who is asking; it invokes the hook and honours the answer: :stop | :wrap-up | :continue.
2. **Stop reason on every turn result and in the transcript**: :end-turn | :loop-limit | :cancelled | :error | :context-exhausted. Logged (`:turn/ended :stop-reason …`) and delivered to comms on turn end.
3. **k4mf bypass closed**: an exhausted turn with empty content can never complete as success, regardless of policy.
4. **The Comm chooses the policy.** on-exhausted lives on the Comm protocol with `comm/defaults` = :stop (today's behaviour: the 'ask me to continue' reply for attended origins — CLI, Discord, ACP). The hail delivery worker's comm answers :wrap-up.
5. **Wrap-up + bounded continuation (hail).** :wrap-up = one final cycle with a system nudge (budget exhausted; start nothing new; commit + push to the bean branch; write the done/next note on the bean; hand off if acceptance is met) and a small tool allowance. Then the delivery worker re-hails the same bean as a fresh turn — a checkpointed continuation, not a nudge inside the degraded context — up to the band's continuation budget. Why this beats a larger cap: a larger cap moves the cliff; the 2fe1 turn compacted 8 times on the way to 400 and would have compacted 16 times on the way to 800 with nothing committed.
6. **Cycle limit is layered**: defaults → crew → dispatcher override on the charge (hail band config, cron job). Interactive origins inherit the crew value. OPEN: rename `tool-loop-max` → `cycle-limit` (clean cutover; touches zanebot crew files + one feature file) — awaiting Micah's yes. Multiple budget currencies (wall-clock, cost) DEFERRED to a separate draft.
7. **Commit on green, always** (work skill): commit + push to the bean branch after every green test run, not only at handoff; the branch is what makes early commits safe (main moves only when the verifier lands). Verify skill step 7a: delete the bean branch after landing. One-time chores: delete merged remote bean branches on every repo — one isaac bean, one tono bean, acceptance-only (no scenarios).
8. **Notify only when continuations are exhausted**: each continuation is logged; attention pages once when the band's continuation budget (hail band config, default 3) runs out; then dead-letter-with-attention as today.

## Structure
This bean is the epic. Children: (a) isaac-agent — stop reason, on-exhausted hook on Comm, k4mf bypass, charge-level cycle limit; (b) isaac-hail — wrap-up policy, continuation budget per band, attention on exhaustion; (c) orchestration skills — commit-on-green, verify deletes the bean branch, tono work skill uses a bean branch; (d) chores — remote bean-branch cleanup (isaac + tono). Scenario plans per child follow, one at a time.



## Children (2026-09-08)
- isaac-y802 — isaac-agent: :ended-by, Comm on-exhausted, :cycle-limit rename (default 100, charge override). Planted affb005 (+ cap-scenario rename). DISPATCHED.
- isaac-xlx1 — isaac-hail: wrap-up policy, checkpointed continuations, budget → attention, band :cycle-limit. Planted e497905. Blocked by isaac-y802; dispatched when isaac-y802 lands.
- isaac-3vil — draft: wall-clock / cost budgets (deferred decision 6).
- Skills (decision 7): DONE — orchestration b413ef7 (work: commit on green, always; verify: delete the landed branch), synced to zanebot (zane-isaac). Chores: 73 merged bean branches deleted across 9 isaac repos; tono has none by design (its band prompt mandates main-only, no branches — Micah's call whether commit-on-green applies there).
- Deploy plan: isaac-y802 ships as agent 0.1.51 with zanebot crew config re-keyed (scrapper `:cycle-limit 120`); isaac-xlx1 ships as hail 0.1.16.



## Summary of Changes (2026-09-08)
All eight decisions landed and are live on zanebot:
- isaac-y802 (agent 0.1.52): `:ended-by` on every turn result + `:turn/ended` log; Comm `on-exhausted` (:stop default, :wrap-up); k4mf bypass closed; `:tool-loop-max` → `:cycle-limit` (built-in 100, charge override); scrapper re-keyed to 120.
- isaac-xlx1 (hail 0.1.16): delivery-worker comm answers :wrap-up; a cycle-limited turn is re-queued as a checkpointed continuation (same delivery, `:continuation` +1, attempts untouched); band `:continuations` (default 3) → `:hail/continuations-exhausted` + attention + dead-letter; band `:cycle-limit` override on the charge.
- Skills: commit on green to `bean/<id>` (work); verify squash-merges to ONE commit per bean and deletes the branch; tono bands moved from main-only to the same branch discipline (zane-isaac 5ecabf8, c677c34); 73 merged bean branches deleted.
- isaac-3vil (wall-clock / cost budgets) stays a standalone draft (decision 6 deferral).



## Post-deploy note (2026-09-10, Micah)
The wrap-up nudge in the drive must stay generic (it drives every turn, not only bean work): agent 0.1.55 ships a neutral nudge — 'save any work in progress the way your instructions say to, then reply with a done/next note'. The git checkpoint instruction lives in the orchestration work skill ('Wrap-up on budget exhaustion'). A deterministic checkpoint run by the delivery worker (isaac-0uim) was scrapped: no shell from config; if the model chooses not to commit that is its freedom — the prompt is the lever.

## Field observation (2026-09-10 10:40Z, isaac-work-1 / isaac-0yoc, grok-4.6)

Cycle limit 120 reached after 371 executed tools; the wrap-up nudge got an empty model response (`:empty-terminal-response: wrap-up note was empty`, 0 tool calls, 0 chars). Path taken: turn `:ended-by :error` → `:hail/attempt-failed` attempts 1 → immediate rebind on the same session, i.e. a retry that skips the checkpoint note and grants a fresh cycle budget. Worked as designed, but an empty wrap-up on a long grok session may be context pressure (request bodies ~650 KB); if it repeats, consider compacting before the nudge or retrying the nudge once at lower effort before failing the turn.
