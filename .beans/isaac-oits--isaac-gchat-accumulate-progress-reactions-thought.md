---
# isaac-oits
title: "isaac-gchat: accumulate progress reactions — \U0001F9E0 thought, \U0001F527 tool, \U0001F4AC aside stay on the triggering message"
status: completed
type: feature
priority: high
created_at: 2026-09-23T19:11:28Z
updated_at: 2026-09-23T19:39:37Z
---

Follow-on to isaac-1bq1. Micah (2026-09-23): "let's go with the accumulate strategy."

## Why

gchat 0.2.10 shows 👀 while a turn runs and swaps it for ✅/⚠️/⏳ at the end. The comm protocol also delivers the middle of the turn — `on-reckoning` (reasoning chunks), `on-tool-call`/`on-tool-result`, `on-aside` (interim text) — and none of it is visible in Chat. A human glancing at the thread wants to know *what kind* of work happened, not a live ticker.

## Design — accumulate, never flicker

- The **first** reasoning chunk of a turn adds 🧠 to the triggering message. The first tool call adds 🔧. The first aside adds 💬. Each is added **once per turn** and **stays**.
- 👀/✅/⚠️/⏳ keep their 1bq1 lifecycle: 👀 on the first cycle, and only that glyph is swapped at the end (remove 👀, add ✅ or ⚠️ or ⏳). The accumulated glyphs are never removed by the module; the message ends up wearing e.g. 🧠🔧✅.
- A parked turn (⏳) that resumes keeps its accumulated glyphs; a later chunk/tool/aside of the same message does not re-add one already present.
- Quota: Chat allows 1 write/sec per space (reaction deletes share it with message posts), 5 reaction creates/sec per space, 600 reaction writes/min per project. Accumulate must stay bounded: at most one create per kind per turn plus the end-state swap — no per-tool or per-chunk writes.
- Reaction state per session grows from one entry to a small map `{kind -> {:message :reaction :emoji}}`; the "current" status glyph is the only entry that is deleted. When a **new** message in the same session starts its lifecycle, the old message's accumulated glyphs are left alone (history); only a stale ⏳ status glyph is evicted as in 1bq1.
- Failures: log once at :debug (`:gchat.reaction/failed`), never retry, never fail the turn — same as 1bq1.
- Config: extend `gchat/reactions` with `:thinking "🧠" :tool "🔧" :aside "💬"` defaults. A kind set to `false` skips that kind; `gchat/reactions false` still turns everything off. Update the manifest value-spec (string or boolean per key) and its description.
- Guidance: unchanged (details on demand already covered).

## Verify against Yopp's real lane

Yopp runs the claude-code **driven** lane. Read `isaac-agent/src/isaac/drive/turn.clj` and the claude-code driver to confirm which of `on-reckoning`, `on-tool-call`, `on-aside` actually fire in driven mode, and whether the gchat comm map registers those hooks at all today. If a hook never fires in driven mode, say so in the handoff (do not fake it) — the feature is still correct for direct lanes.

## Acceptance (features/comm/gchat/outbound.feature, RX6+)

- [ ] Two reasoning chunks in one turn → exactly one reactions.create with 🧠 on the triggering message.
- [ ] Three tool calls in one turn → exactly one reactions.create with 🔧.
- [ ] One aside → one reactions.create with 💬.
- [ ] Turn with thinking + tool + reply → the message carries 🧠 🔧 ✅; only 👀 was deleted; 🧠 and 🔧 were never deleted.
- [ ] Park then resume with another tool call → no second 🔧; ⏳ swapped for ✅ when the answer posts; 🧠/🔧 still present.
- [ ] `gchat/reactions {:tool false}` → no 🔧 create; other kinds unaffected. `gchat/reactions false` → no calls at all.
- [ ] Spec: per-turn "already added" guard resets when a new triggering message starts.
- [ ] Handoff states which intermediate hooks fire in the claude-code driven lane.
- [ ] Manifest 0.2.11; `bb spec`, `bb features`, `bb lint` on src/ green.

Likely repo scope: isaac-gchat (`src/isaac/comm/gchat.clj`, `chat_api.clj` unchanged, manifest, outbound.feature, gchat_steps.clj). Read-only in isaac-agent.


## Handoff

Branch `bean/isaac-oits` pushed to `isaac-gchat`, commit `ee6ccd7036759339ddab0e752d8dabcb2674fb3b`.
Bean left `in-progress`, no tags — planner verifies and lands.

### Files changed

- `src/isaac/comm/gchat.clj` — accumulated 🧠/🔧/💬 (`reaction-accumulate!`,
  `on-reckoning*`/`on-tool-call*`/`on-aside*`), per-session reaction state
  reshaped to a slot map (`:status`/`:thinking`/`:tool`/`:aside`), guard
  reset on a new triggering message (`reset-accumulated!`).
- `resources/isaac-manifest.edn` — version 0.2.11; `:gchat/reactions`
  description + defaults extended; fixed `:one-of` spec ordering (see below).
- `features/comm/gchat/outbound.feature` — RX6, RX7, RX8.
- `spec/isaac/comm/gchat_spec.clj` — 8 new unit specs.

### Gate

- `bb spec`: 171 examples, 0 failures.
- `bb features`: 54 examples, 0 failures.
- `bb lint src spec feature-steps`: 0 errors, 0 warnings.

### Acceptance checklist

- [x] Two reasoning chunks in one turn → exactly one reactions.create with 🧠
      (RX6: two reasoning cycles, only the first creates 🧠).
- [x] Three tool calls in one turn → exactly one reactions.create with 🔧
      (RX7).
- [x] One aside → one reactions.create with 💬 (also RX7 — three tool-call
      cycles produce three aside events, only the first creates 💬; a
      dedicated single-aside scenario would be redundant with this one).
- [x] Turn with thinking + tool + reply → message carries 🧠 🔧 ✅; only 👀
      was deleted; 🧠 and 🔧 never deleted (RX6, full 5-request sequence
      asserted by index, 1 delete total).
- [x] Per-turn "already added" guard resets when a new triggering message
      starts (spec: "resets the already-added guard when a new triggering
      message starts its lifecycle").
- [x] gchat/reactions {:tool false} → no 🔧 create; other kinds unaffected
      (RX8, feature-level, after the manifest ordering fix below).
      gchat/reactions false → no calls at all (RX5, pre-existing, unchanged).
- [~] Park then resume with another tool call → no second 🔧; ⏳ swapped for
      ✅ when the answer posts; 🧠/🔧 still present. Satisfied at the **spec**
      level only ("does not keep a stale guard across a park/resume of the
      same message"), not as a new feature scenario — matching 1bq1's own
      precedent (its analogous same-message-resume case is a spec too, not a
      feature). Reason: a *feature*-level "resume" is realistically a second,
      distinct inbound Chat message (see RX3), which legitimately gets its
      own fresh glyphs since Chat reactions are per-message; the guard this
      bullet is about — the same origin `:message` resuming without a new
      inbound event — is an internal turn-continuation, which the spec
      exercises directly and precisely.
- [x] Manifest 0.2.11; bb spec, bb features, bb lint on src/ green.

### Driven-lane hooks (isaac-claude-code, read-only)

Read `isaac-agent/src/isaac/drive/turn.clj` and
`isaac-claude-code/src/isaac/llm/api/claude_cli.clj`. In the claude-code
**driven** lane (`:drives-tool-loop? true`, `tool-loop/run` dispatches to
`claude-loop-driver` instead of `-run-default`):

- `on-cycle-start` / `on-cycle-end` — fire. The driver calls the `on-cycle`
  hook it's given (`fire-on-cycle!` at `:start`/`:end`); turn.clj's own
  `on-cycle` closure is what actually calls `comm/on-cycle-start` and, on
  `:end`, either `comm/on-reply` (no tool calls) or arms `pending-aside*`.
- `on-reckoning` — **fires**. The driver is handed the same streaming
  `chat-fn` turn.clj built via `chat-fn-for` (the request has tools, so the
  streaming branch is used); `stream-once` in claude_cli.clj parses
  `reasoning-delta` lines out of the CLI's stream-json output and the
  streaming `on-chunk` wrapper calls `comm/on-reckoning` for each one.
- `on-tool-call` / `on-tool-result` — **fire**. `claude-loop-driver` resets
  `drive-tool-fn*` to the `tool-fn` it was given (`record-tool-call!`,
  which calls `announce-tool-call!` → `comm/on-tool-call`, runs the tool,
  then `comm/on-tool-result`). The real MCP path (`register-mcp-turn!` →
  `mcp-listener`/`mcp-bridge`) registers that same tool-fn for the CLI's
  MCP tool calls; the stream-json tool-use parser also invokes
  `@drive-tool-fn*` directly for accounting. Either way it's the same
  `record-tool-call!`, so `comm/on-tool-call` fires for real.
- `on-aside` — fires the same way as the default lane: turn.clj's `on-cycle`
  arms `pending-aside*` on a tool-bearing cycle, and `announce-tool-call!`
  calls `end-aside!` right after `comm/on-tool-call`, which fires
  `comm/on-aside`.

So all three intermediate hooks this bean cares about (on-reckoning,
on-tool-call, on-aside) genuinely fire in the driven lane — none of them
are direct-lane-only. gchat's `extend`/`comm/defaults` wiring is
lane-agnostic (it only implements `Comm`), so no gchat code change was
needed to make this true; it was already the case going in, and RX6-RX8
exercise the same turn.clj call sites the driven lane uses (Grover/echo is
a **non-driven** fixture — this repo has no automated coverage of the real
claude-cli process — so this answer rests on source reading, not a passing
test with the real CLI).

### API calls for a thinking + tool + reply turn (Chat side)

For a turn that reasons once, calls one tool, and then replies (see RX6's
sequence), gchat.clj issues, in order:

1. `POST /v1/{message}/reactions` — 👀 (`reaction-working!`, cycle 1 start)
2. `POST /v1/{message}/reactions` — 🧠 (`reaction-accumulate! :thinking`, first `on-reckoning`)
3. `POST /v1/{message}/reactions` — 🔧 (`reaction-accumulate! :tool`, `on-tool-call`)
4. `POST /v1/{message}/reactions` — 💬 (`reaction-accumulate! :aside`, `on-aside`)
5. `DELETE /v1/{message}/reactions/{id}` — removes 👀 (`reaction-done!` → `set-reaction!`, `on-reply`)
6. `POST /v1/{message}/reactions` — ✅ (`reaction-done!`, same call)
7. `POST /v1/{space}/messages` — the reply text itself (`reply!`/`post-chunks!`)

🧠 and 🔧 are never deleted by this module.

### Not satisfied / simplifications

- No feature-level scenario for the exact same-message park/resume case
  (see checklist above) — covered at spec level instead, by design,
  matching 1bq1's own precedent.
- Found and fixed a pre-existing bug in the manifest's `:gchat/reactions`
  value-spec: `:one-of` tries its `:specs` in order and `:boolean`/`:string`
  coercion both "succeed" on any input, so whichever was listed first
  silently swallowed the other shape (a map override became bare `true`; a
  per-key `false` became the string `"false"`). Fixed by putting the more
  specific spec first at both levels. This was latent since 1bq1 (a
  feature-level `gchat/reactions` map override was never exercised before
  this bean) — flagging in case it's worth a note for other manifests using
  `:one-of` with `:boolean`/`:string` alternatives.

## Landed on main

main-sha: isaac-gchat ee6ccd7036759339ddab0e752d8dabcb2674fb3b (0.2.11). Planner reran bb spec (171/0) and bb features (54/0, JVM pass included), pushed to main, deleted bean/isaac-oits, repinned the registry.
