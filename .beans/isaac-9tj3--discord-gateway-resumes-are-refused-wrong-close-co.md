---
# isaac-9tj3
title: 'Discord gateway resumes are refused: wrong close code and URL, collapsed backoff, noisy closes'
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-11-17T15:07:02Z
updated_at: 2026-11-17T15:20:56Z
---

Approved by Micah 2026-11-17 from Zane Lab's findings. Ungated: worker, then
verifier.

## Planner notes

- Start from the lab's reviewed patch on zanebot:
  `/Users/zane/agents/zane-lab/experiments/discord-resume/fix.v2.patch`. It was written
  against `isaac-discord e820a97`; main may have moved, so rebase by hand. Read the
  reviewer's `REVIEW.md` in the same experiment first: it says what the first
  patch got wrong and what v2 changed. You own the result; the patch is a
  reference, not a contract.
- The lab's text below is its own draft. Where it and the code disagree,
  the code wins; say so in the bean.
- Never signal a process group or use `pkill`/`killall` in any check you
  run; the lab's `check.sh` scripts do, so do not run them as they are.
- `bb ci` and `bb jvm-spec` pass. No version bump; that happens at deploy.
- The patch reads a private field of isaac-agent's websocket wrapper to send
  close code 4000. Keep that only if it fails loudly at load when the field
  is renamed (the reviewer found v1 failed silently). The clean fix, a close
  code on isaac-agent's `ws-close!`, is a follow-up, not this bean.
- The acceptance block below is the behaviour to prove, in whatever mix of
  specs and feature scenarios fits the repo's existing steps; all twelve of
  the reviewer's specs in the v2 patch must pass.

---

# Bean (ready): Discord gateway resumes are refused because Isaac closes with 1000 and dials the wrong URL; reconnect backoff collapses; routine closes log as warnings

- Repo: isaac-discord (live `e820a97`), one file: `src/isaac/comm/discord/gateway.clj`
- Class: real bug (×3) + wrong log level
- Patch: [`fix.patch`](fix.patch) (src + new `gateway_resume_spec.clj` + one feature-level line + `deps.edn` spec list)
- Supersedes the drafts `log-bug-hunt/beans/discord-resume-gateway-url.md`,
  `discord-gateway-close-noise.md` and `discord-reconnect-backoff.md`.
  Two of their stated causes were wrong; see "What changed from the drafts".

## Why one bean

All four fixes are in the close → reconnect path of one file, and they share one gate, `reconnect.feature`.
The resume fixes (URL and close code) only work together. The reader-loop
change the log-level fix needs also removes the double close. Split into
four beans, each would re-review the same ~100 lines and rerun the same features.

## What is wrong

1. **Isaac invalidates its own session before resuming.** On opcode 7, `on-close!` →
   `transport-close!` → isaac-agent `RealWs/ws-close!` →
   `sendClose(WebSocket/NORMAL_CLOSURE)`, which is **1000**. Discord: *"When you close the
   connection to the gateway with close code `1000` or `1001`, your session will be
   invalidated … If you simply close the TCP connection or use a different close code,
   the session will remain active"* ([Initiating a Disconnect](https://docs.discord.com/developers/events/gateway#initiating-a-disconnect)).
   The RESUME that follows has nothing to resume, so Discord answers with op 9 `d:false`.
2. **RESUME dials the default gateway.** `handle-dispatch!` "READY" drops
   `resume_gateway_url`, and `do-reconnect!` always dials `(:url client)`. Discord: *"open a
   new connection using `resume_gateway_url` rather than the URL used to initially connect,
   with the same query parameters … If your app doesn't use the `resume_gateway_url` …
   it will experience disconnects at a higher rate than normal"* ([Preparing to Resume](https://docs.discord.com/developers/events/gateway#preparing-to-resume)).
3. **Re-IDENTIFY after op 9 does not wait.** Isaac reconnects after the 1 s base delay.
   The older gateway docs said: *"is expected to wait a random amount of time (between 1
   and 5 seconds), then send a fresh OP 2 Identify"* ([discord-api-docs @c909be9](https://github.com/discord/discord-api-docs/blob/c909be96a8410e3ad4f29a24501943c126fc43d7/docs/topics/Gateway.md)).
   Today's docs dropped the number but keep the reason: *"concurrent Identify requests
   allowed per 5 seconds. If you hit this limit, the Gateway will respond with an Invalid
   Session (opcode 9)"* ([Rate Limiting](https://docs.discord.com/developers/events/gateway#rate-limiting)).
4. **The backoff collapses after a slow connect failure.** Reconnect used the scheduler's
   `:on-error :retry`. isaac-foundation `after-error` sets the next fire to
   `scheduled-at + backoff`, anchored to when the failed run was *scheduled*, not when
   it failed. A connect that hangs 30 s before `ConnectException` puts every remaining
   retry in the past, so they all run back to back.
5. **Routine closes log as warnings.** Each op 7 cycle logs `warn reconnect-requested`,
   2× `warn disconnected` (the second from the superseded reader loop, which calls
   `on-close!` again), 2× `warn heartbeat-cancelled`. Each stop logs `warn disconnected`
   and `error transport-close-failed`: the reader sees the stop, and its close of the
   already-closed socket fails.

## Evidence (keys, counts and timings only; `recount.bb`)

| | last 24 h (to 2026-11-17 03:26Z) | all logs (2026-10-18 →) |
|---|---|---|
| RESUME sent | 7 | 282 |
| refused (op 9 `d:false` ≤ 5 s after RESUME) | **7** | **281** (the 282nd got op 9 at 7.1 s) |
| opcode 7 received | 7 | 282 |
| IDENTIFY sent | 18 | 420 |
| reconnect attempt n>1 < 1 s after the previous one | 0 | **82** |
| `scheduler/handler-error` for the Discord reconnect job | 0 | 135 |
| `reconnect-exhausted` | 0 | 13 |
| warn/error lines from `discord.gateway/*` | **89** | 2712 |

The 2026-11-15 outage, timestamps and `:scheduled-at` from the log:

```
16:34:34.92 attempt 1            (scheduled 16:34:34.89)
16:35:04.93 handler-error        scheduled-at 16:34:34.89   <- connect hung 30 s
16:35:04.93 attempt 2            scheduled-at 16:34:35.89 (+1 s, already past)
16:35:04.97 attempt 3            scheduled-at 16:34:37.89 (+2 s, past)
16:35:05.04 attempt 4            scheduled-at 16:34:41.89 (+4 s, past)
16:35:05.09 attempt 5            scheduled-at 16:34:49.89 (+8 s, past)
16:35:06.04 attempt 6            scheduled-at 16:35:05.89 (+16 s)
16:35:35.89 attempt 7            scheduled-at 16:35:35.89 (+30 s cap); hung 30 s
16:36:05.90 attempt 8 -> reconnect-exhausted
```

There was one timer, not two. The draft's "two reconnect timers" inference was wrong.

End to end, a fake gateway on 127.0.0.1:8713 follows only the documented
close-code rule. It drives the real client over a real `java.net.http` WebSocket (`e2e.clj`):

```
before (e820a97)                          after (patched)
[:sent-op7]                               [:sent-op7]
[:close :normal]                          [:close :unknown]          <- 4000
[:session-invalidated]
[:connect "/"]                            [:connect "/resume/"]
[:resume "/" :refused-op9]                [:resume "/resume/" :accepted]
[:connect "/"] [:identify]                (RESUMED; no second IDENTIFY)
client warn/error: 11 lines               client warn/error: {}
```

## The fix

- READY stores `:resume-gateway-url`. A `:resume` reconnect dials
  `<resume_gateway_url>/?v=10&encoding=json`, using the query from the initial URL and
  tolerating a trailing slash. `:identify`, or a resume with no stored URL, dials the default URL.
- A close that intends to resume sends **4000**. Stop and identify-closes still send 1000.
  For the real transport this reaches `RealWs`'s `websocket` field (`sendClose code`); map
  transports may provide `:close-with-code!`.
- Op 9 `d:false` forgets the session (id, resume URL, seq) and schedules the IDENTIFY in
  `[1000, 5000)` ms.
- The gateway owns the backoff. Each attempt is its own one-shot task, scheduled from the
  failure time: `d = min(cap, base·2^(n-1))`, delay uniform in `[d/2, d]` (equal jitter),
  cap 30 s by default, budget still 8. A failure logs `warn discord.gateway/reconnect-failed`
  with `:error-class` and `:next-delay-ms`, and the scheduler no longer logs `handler-error`.
  `:rand-fn` is injectable for specs.
- A reader loop runs only while its transport is the client's current one. A superseded or
  stopped reader exits with `debug reader-superseded` and does not call `on-close!`.
  `stop!` clears `:transport` before closing.
- Levels: `heartbeat-cancelled` → debug; `reconnect-requested` → info; `disconnected` → info
  for op 7 / op 9 and warn otherwise; `transport-close-failed` → debug (closing a dead socket
  is not actionable). `invalid-session`, `reconnect-exhausted`, `fatal-close`, heartbeat-ack
  timeouts and real transport errors keep their levels.

## Acceptance

```gherkin
Feature: Discord gateway resume and reconnect

  Scenario: an opcode 7 reconnect resumes on READY's resume_gateway_url
    Given READY carried session_id "sess-1" and resume_gateway_url "wss://resume.example"
    When the gateway receives opcode 7
    Then the old socket is closed with code 4000
    And the next connection opens "wss://resume.example/?v=10&encoding=json"
    And a RESUME with session_id "sess-1" and the last sequence is sent

  Scenario: stopping closes with 1000
    When the Discord client is stopped
    Then the socket is closed with code 1000

  Scenario: opcode 9 d=false re-identifies after a 1-5 s wait on the default URL
    Given READY carried resume_gateway_url "wss://resume.example"
    When the gateway receives opcode 9 with d false
    Then no connection opens for at least 1000 ms
    And a connection opens on the default gateway URL within 5000 ms
    And an IDENTIFY is sent
    And the stored session id and resume URL are cleared

  Scenario: failed attempts back off from the failure, with jitter and a cap
    Given every reconnect fails and each connect takes 30 s to fail
    When the gateway disconnects with status 1006
    Then attempt n+1 starts no sooner than d/2 after attempt n failed, d = min(cap, base * 2^n)
    And no delay exceeds the cap
    And the log has warn ":discord.gateway/reconnect-failed" with the exception class
    And the log has no ":scheduler/handler-error"
    And the client still parks after 8 attempts

  Scenario: routine closes are not warnings
    When the gateway receives opcode 7 and resumes
    Or the Discord client is stopped
    Then the log has no warn or error entries from ":discord.gateway/*"
    And a superseded reader loop does not run on-close a second time
```

Proven by `spec/isaac/comm/discord/gateway_resume_spec.clj` (15 examples: 12 red on
e820a97, all green with the fix) and `e2e.clj`. `reconnect.feature` now expects
`:info reconnect-requested`.

## Numbers to watch after deploy (`recount.bb 24`)

| metric | before (24 h) | after |
|---|---|---|
| refused resumes / RESUME sent | 7 / 7 | ≈ 0 / 7 |
| IDENTIFY per day | 18 | ≈ 11 (restarts and reloads only: one fewer per op 7) |
| invalid-session `:resumable false` | 7 | ≈ 0 |
| warn/error lines from `discord.gateway/*` | 89 | ≈ 1–2 (genuine drops only, e.g. the 1 heartbeat timeout) |
| rapid attempts (n>1, < 1 s apart) / discord `handler-error` | 0 / 0 (82 / 135 all-time) | 0 / 0. This moves only on an outage, so watch `recount.bb all` over weeks. |

Quality check: none of this touches model input or output. If resumes still get op 9
after deploy, the close code was not the cause: look at timing or Discord-side session
expiry next. The URL fix stays doc-mandated either way.

## Risk

- **Reaches into isaac-agent's `RealWs`** (`.-websocket` field) to send a code other than 1000.
  This is a type-hinted field access, so a rename fails at load, not silently. The clean
  follow-up is a `ws-close!` arity with a code in isaac-agent.
- A 4000 close leaves the Discord session alive for a few minutes if the resume then fails
  and Isaac re-identifies. That is harmless: it times out server-side.
- Op 9 now waits 1–5 s instead of 1 s, so a real invalid session can cost up to 4 s more.
- Reconnect delays are now random, within `[d/2, d]`. The first reconnect after a close is
  0.5–1 s, where it was exactly 1 s.
- Superseded readers drop frames that arrive on a replaced socket. Those frames belong to
  the dead connection.
- Behaviour and code are unchanged for close 1006 / no close code. Discord says to *resume*
  there, but Isaac re-identifies. That is a separate follow-up.

## Ship order and follow-ups (drafts, not in this patch)

1. This bean (isaac-discord). No dependency on the others.
2. isaac-foundation: `after-error` should anchor `:retry` backoff to the failure time
   (`max(now, scheduled-at) + backoff`). The same collapse can hit the other live `:retry`
   user, `isaac.comm.imessage` (4391aaa). Also: `scheduler.runtime` uses `:present-when?`
   without requiring `config.validation-lexicon`, so a spec file run alone fails with
   "missing lex :present-when?".
3. isaac-agent: `ws-close!` with a close code, then drop the field access here.
4. isaac-discord: resume (not identify) on 1006 / no close code, per the Resuming list.

## What changed from the drafts

- The op 9 cause was not only the URL. Isaac's own 1000 close is the documented cause. The
  URL is still required, and both are fixed. The e2e fake does not enforce the URL (Discord
  does not document a refusal), so it can't say how much each contributed live.
- There were no "two reconnect timers". The collapse comes from the scheduler's
  `scheduled-at` anchoring after a 30 s connect hang.


## Implementation notes (2026-11-17)

- isaac-discord bean/isaac-9tj3 commit c382c64. READY resume URL and seq persist; resumable closes use 4000; invalid sessions wait before fresh IDENTIFY; retry jitter is scheduled from failure; stale readers exit without duplicate close; routine lifecycle logs are quieter. After two failed resume dials, fall back to default URL and IDENTIFY; stop during a dial does not adopt its socket.
- The lab draft asserted a type hint would fail at load if RealWs.websocket were renamed. The reviewer demonstrated this was false. The implementation resolves the field on JVM namespace load with Class.getField (missing field throws), while skipping JVM reflection in Babashka SCI, where RealWs is an SCI type; the review spec remains a JVM canary. The draft wording is superseded by this implementation.
- Acceptance: ISAAC_GIT=1 bb ci passes (native 56/0, JVM 136/0 including all 12 reviewer examples, features 70/0). No version bump. No external gateway contacted. Gate exit 2: no feature-baseline; verification required.



## Verification failed (2026-11-17)

HEAD: isaac-discord c382c64502857de55c05c72b8d907d0ad1b83f12 (origin/bean/isaac-9tj3)
Working tree: clean (detached verification worktree); isaac beans main clean before verdict.

Feature integrity gate failed before tests. `git diff origin/main...origin/bean/isaac-9tj3 -- features/comm/discord/reconnect.feature` changes line 58 from `| :warn | :discord.gateway/reconnect-requested |` to `| :info | :discord.gateway/reconnect-requested |`. This is not an @wip removal. The bean has no `## Exceptions` section authorizing this exact edit; the acceptance prose does request the new info level, but the verifier protocol explicitly requires a `## Exceptions` entry for any non-@wip feature edit. Document this intended exception in the bean and return for verification. Tests were not run because the /verify command stops at the first integrity failure. No implementation was landed on main.



## Held (awaiting planner, 2026-11-17)

Verification stopped at feature integrity: `features/comm/discord/reconnect.feature:58` changes `:warn` to `:info` for `:discord.gateway/reconnect-requested`. This is required by this bean's acceptance and the implementation; it is not an `@wip` removal. The verifier requests an explicit `## Exceptions` entry naming this exact change. Worker protocol forbids workers from authoring `## Exceptions` or altering feature contracts, including on ungated beans. Planner must authorize this exception in the bean, then redispatch for verification; no source or feature edits are justified. Existing implementation remains on `isaac-discord` `origin/bean/isaac-9tj3` at c382c64502857de55c05c72b8d907d0ad1b83f12. Gate exit 2 (no `feature-baseline:`); bean remains in-progress. Escalated by **scrapper**@bean-isaac-9tj3. Resumes only on explicit planner action.


## Exceptions

Authorized by the planner, 2026-11-17. These feature edits are part of this bean and are not tampering:

- `features/comm/discord/reconnect.feature`: log-level cells for routine gateway lifecycle events change from `:warn` (or `:error`) to `:info` or `:debug`, including line 58, `:discord.gateway/reconnect-requested` from `:warn` to `:info`. Routine means an opcode 7 reconnect that resumes, and a client stop. A failed reconnect attempt stays at warn.
- Any feature file in this bean's repo: adding new scenarios, and new step definitions for them, that prove the acceptance above. No existing scenario may be removed or weakened beyond what is listed here.



## Re-handoff for verification (2026-11-17)

Planner authorized the reconnect.feature:58 :warn → :info edit in ## Exceptions after the earlier integrity hold. No further feature or source edits needed; prior hold is superseded. Implementation remains isaac-discord origin/bean/isaac-9tj3 at c382c64502857de55c05c72b8d907d0ad1b83f12. Re-ran ISAAC_GIT=1 bb ci on that branch: 136 JVM specs, 70 features, zero failures; native suite green. Re-ran bb bean-gate verify isaac-9tj3: exit 2 (no feature-baseline). Ready for ungated verification; status stays in-progress.
