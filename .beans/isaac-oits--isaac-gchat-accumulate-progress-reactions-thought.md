---
# isaac-oits
title: "isaac-gchat: accumulate progress reactions — \U0001F9E0 thought, \U0001F527 tool, \U0001F4AC aside stay on the triggering message"
status: in-progress
type: feature
priority: high
created_at: 2026-09-23T19:11:28Z
updated_at: 2026-09-23T19:11:28Z
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
