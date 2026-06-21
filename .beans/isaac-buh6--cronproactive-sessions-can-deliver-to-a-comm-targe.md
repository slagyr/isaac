---
# isaac-buh6
title: Cron/proactive sessions can deliver to a comm target (retire null-comm + osascript)
status: todo
type: feature
priority: high
created_at: 2026-06-21T15:48:10Z
updated_at: 2026-06-21T17:47:56Z
blocked_by:
    - isaac-ve2a
---

A cron job runs its prompt and DISCARDS the output — `isaac.cron.service` wires
every cron session to `null-comm/channel` with `:origin {:kind :cron …}`. The
cron schema has only `:crew`/`:expr`/`:prompt`; there is no way to address a
real comm. So a proactive session is NOT connected to any channel, and the only
way to get a message out is the agent shelling `osascript`→Messages.app (see
zanebot `config/cron/health-checkin.md`). That AppleScript path is flaky (120s
timeouts) and bypasses `imsg` entirely.

Goal: let a proactive (cron, and later hook) session be addressed to a comm +
recipient, so its origin IS that comm and the agent's normal response delivers
through it (`imsg send` for imessage) — no AppleScript, no special tool.

## Tasks
- [ ] Cron schema: add a delivery target (e.g. `:comm :imessage` + `:to "<handle>"`,
      shape TBD) on a cron entry; validate the comm exists.
- [ ] Cron service: when a target is set, run the session against THAT comm +
      recipient (not `null-comm`), so the response is delivered like a reply.
      Default (no target) keeps today's discard behavior.
- [ ] Rewrite zanebot `config/cron/health-checkin.md`: delete the `osascript`
      block; the prompt just produces the alert/summary text, Isaac delivers it.
- [ ] Consider generalizing the same addressing to hooks / any proactive session
      (origin framing — see ho1s). Out of scope to implement here; note the seam.

## Dependency
- **Blocked by ve2a** — the delivery worker must resolve the live comm instance
  first; otherwise a targeted cron just dead-letters `:permanent`.

## Acceptance
- A cron entry addressed to the imessage comm delivers its output to the
  configured recipient via `imsg send` (over the ssh wrapper), no osascript.
- `health-checkin.md` contains no `osascript`; the 9 AM health review sends
  through the comm.
- Untargeted crons (e.g. heartbeat) still run-and-discard as today.

## Scenarios (DRAFT — pending review; do not generate feature file yet)
```gherkin
Scenario: a cron addressed to a comm delivers its output through that comm
  Given a cron entry "health" with crew main, :comm :imessage, :to "micahmartin@mac.com"
  When the cron fires and the session produces a response
  Then the response is delivered to "micahmartin@mac.com" via the imessage comm (imsg send)
  And  the cron session does NOT use the null comm

Scenario: an untargeted cron runs and discards output (unchanged default)
  Given a cron entry "heartbeat" with no comm target
  When the cron fires
  Then the session runs against the null comm and no message is delivered

Scenario: a cron targeting an unknown comm is rejected at config load
  Given a cron entry with :comm :nope
  When config is loaded
  Then validation reports that comm :nope does not exist
  And  the config is invalid
```

## Scenarios written (2026-06-21) — supersedes the draft block above

Re-themed to the Marigold (spaceship) world and committed `@wip`:

- **Scenarios 1 & 2** → `isaac-cron` `features/delivery.feature` (`@wip`, pushed
  `d5f4e2c`): (1) Cordelia's dawn watch report delivers via the `longwave` comm;
  (2) the untargeted `hull-check` cron discards (skybeam/null default), delivery
  queue stays empty.
- **Scenario 3 (validate undefined comm)** → a **SPEC**, not a feature. Cron's
  `config_validate.feature` was retired; config-validate is now spec-tested
  (`isaac-foundation/spec/isaac/config/cli/command_spec.clj`). So implement the
  `:comm-exists?` cron-schema validation + a unit spec mirroring `:crew-exists?`.

## Implementation tasks (DoD: remove `@wip`, green)
- [ ] Cron schema: `:comm` + `:to` fields; `:comm-exists?` validation.
- [ ] Cron service: when `:comm` set, enqueue a delivery to that comm + `:to`
      (the async path — see ve2a) instead of `null-comm`. No target = discard.
- [ ] New step `the delivery queue is empty` (negative assertion on the queue).
- [ ] Parameterize `with-stub-comm` (isaac-agent `worker_steps.clj`) so a
      scenario can register/target a named ship comm (`longwave`/`skybeam`/
      `logbook`) rather than the hardcoded `"stub"`.
- [ ] `:comm-exists?` spec (cron) for the undefined-comm case.

## Acceptance (runnable)
- `bb features features/delivery.feature` in `isaac-cron` — green (after `@wip`
  removed).
- `bb spec` covering the `:comm-exists?` validation — green.

## Dependency
- Still **blocked by ve2a** (delivery worker must resolve the live comm).
