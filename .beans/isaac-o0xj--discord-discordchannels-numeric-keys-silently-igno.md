---
# isaac-o0xj
title: Discord :discord/channels numeric keys silently ignored for :session/:crew overrides
status: completed
type: bug
priority: normal
created_at: 2026-06-25T19:06:44Z
updated_at: 2026-06-25T21:55:53Z
---

## Context
Discord messages on a configured channel (e.g. #tempest, snowflake 1491164414794272848) did not route to the declared :session "discord-tempest" (with tempest crew + gpt-5.4). The live bot used the fallback "discord-1491164414794272848" session + claude. Direct ACP to "discord-tempest" showed different crew/model/CWD. User had the mapping in isaac.edn.

## Reproduction
- Configure :comms {:discord {:discord/channels {1491164414794272848 {:session "discord-tempest" :crew "tempest"}}}} (bare number key, common for snowflakes).
- Restart or wait for config; post a message or "tell me about our session" in the channel.
- Bot reports the discord-<id> session and resolves default crew/model.
- ACP --session discord-tempest reaches a different session.
- Using string key "1491164414794272848" makes the override take effect on next message.

See routing.feature "per-channel session override routes to the configured session".

## Root cause
```clojure
;; isaac-discord/src/isaac/comm/discord.clj:46
(defn- channel-config [discord-cfg channel-id]
  (or (get-in discord-cfg [:discord/channels (keyword (str channel-id))])
      (get-in discord-cfg [:discord/channels (str channel-id)])
      {}))
```
- Forward lookup only matches exact string/keyword-str forms.
- `session->channel-id` does `(str channel-id)` on keys (more forgiving) but still walks the raw map.
- EDN bare numbers become Long; test `parse-value` also injects Longs for digit strings.
- Schema declares `:key-spec {:type :string}` (see manifest + c3kit coercion in process-dynamic-entry) and load does `conform` on slices, which *should* coerce keys via `(str v)`. However raw snapshots, overrides, hot-reload `set-snapshot!`, legacy `[:channels :discord]`, and the integration `@cfg` atom can still surface non-string keys.
- Thus overrides are silently missed; `ensure-session!` binds the wrong name; crew/model fall back.
- CWD forcing in `create-session!` is a related inconsistency for named sessions.

Related: dcr1 (discord comm state), hrl1 (config visibility), m4bi (comm contributions).

## Acceptance
- After `load-config-result` (or equivalent) on config containing a bare-numeric (Long) channel ID key under `:discord/channels`, the resulting `:config` has that key as string and `channel-session-name` / `channel-crew` / `channel-model` return the declared overrides.
- The existing scenario "per-channel session override routes to the configured session" in `isaac-discord/features/comm/discord/routing.feature:50` continues to pass unchanged.
- Add (or the test harness via `parse-value` which turns pure-digit table values into Longs) a gherkin scenario exercising a pure-numeric channel id (e.g. channel_id 1491164414794272848 or "1491164414794272848") with a `:session` override; it must route to the named session and not the `discord-<id>` fallback. Run with `bb features` (or `cd isaac-discord && bb features`).
- `session->channel-id` (reverse) also handles Long/string/keyword keys from the channels map and returns the correct channel id string.
- Existing string-key configs and all current specs/features remain green; no behavior change when keys are already strings.
- A small normalization (or tolerant lookup) is present so raw EDN Long keys or test-injected Longs never cause fallback routing. (See handoff.)
- A message arriving after config with numeric-key override produces/uses the declared session name (verifiable via transcript or session name in test).

## Notes / open questions
- Normalize once (small dedicated helper) vs. make every lookup tolerant?
- The schema coercion already "forces" strings on conform paths — we should lean on it but not depend exclusively on every call site seeing a conformed value.
- Document in README (keys are strings; use quotes in EDN for snowflakes).
- Legacy `discord-<id>` sessions: no auto-migration for now (user can delete file or use the id-named session); just make new overrides take effect reliably.
- Related timing/hot-reload issues may still need attention (see dcr1, hrl1).

## Small dedicated normalizer exploration (proposed)
A tiny pure helper near the channel fns:

```clojure
(defn- normalize-channels [discord-cfg]
  (let [chs (get-in discord-cfg [:discord/channels])]
    (if (map? chs)
      (assoc discord-cfg :discord/channels
             (into {} (map (fn [[k v]] [(str k) v]) chs)))
      discord-cfg)))

;; Then in channel-config, session->channel-id, and/or discord-config:
(let [dcfg (normalize-channels discord-cfg)]
  (get-in dcfg [:discord/channels (str channel-id)] ...))
```

Apply early in `discord-config` (after the merge) and/or in `effective-config` result for discord. This makes both directions canonical (strings) and makes the schema declaration effective in practice. Cheap, local, no behavior change for already-string keys. Can also be used in tests to assert post-load shape.

## Handoff
1. Introduce (or inline) normalization so Long/kw/str keys all resolve.
2. Extend `routing.feature` (or add numeric scenario) so a table value that parses to Long (pure digits) exercises the override.
3. Assert in a spec or after load-config-result simulation that channels keys are strings and overrides visible.
4. Update README example comment if helpful.
5. Keep all existing specs + features green.
6. Update bean to completed + unverified when done.


## Schema coercion analysis (2026-06-25)
- The manifest already declares exactly `:key-spec {:type :string}` on `:discord/channels`.
- c3kit.apron.schema key coercion: for a map spec with `:key-spec`, `process-dynamic-entry` does `k' = (-process-spec-on-value process key-spec k)`, then `assoc` with the new key.
- `:string` uses `(some-> v str)` → Long 149... becomes "149...".
- Config load: `load-config-result` → root `lexicon/conform` (detects errors, keeps raw) + `conform-berth-slices` (for `:comms`) which does `lexicon/conform` on the slice and *replaces* with conformed on success.
- `:comms` value-spec has `:dynamic-schema` pulling extra-schema (incl. the channels map spec with its key-spec). Conform should walk into comm slot map → `:discord/channels` field → coerce its keys.
- `process-message!` always does fresh `(effective-config ... (load-config-result ...))` then `discord-config`, so should see coerced strings.
- `discord-config` also falls back to legacy `[:channels :discord]`.
- Yet symptom occurred (override ignored → id-derived session used).
- Likely causes: raw data in some snapshot/hot-reload/set-snapshot! path; override not present at first message for the channel (ensure-session! binds the name); key was under wrong nesting; or @cfg atom in integration held a non-fully-coerced slice for other paths.
- Lookup itself (`channel-config`) only tries exact `(str k)` and `(keyword (str k))` get-ins — does not normalize source keys.

Conclusion on the suggestion: the schema *is* the place declaring "force string keys" and the coercion machinery implements it for conform paths. Making it more robust means (a) trusting the conformed data, (b) adding defensive normalization right where discord extracts/uses the channels map, and (c) keeping the declaration.

Update to bean ACs: add that after load-config-result (or simulate with numeric key in test data), channels map keys are strings and overrides apply.
