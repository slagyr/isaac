---
# isaac-j1ju
title: 'Session readout bugs: /status cwd from process; session_info can''t resolve upstream model'
status: completed
type: bug
priority: normal
created_at: 2026-05-14T17:19:05Z
updated_at: 2026-05-14T18:21:50Z
---

Two related bugs in session-state readouts surfaced while testing `/cwd` on Marvin (zanebot, `tidy-comet` session). Both are presentation/lookup bugs — the session on disk is fine; the code reading it isn't.

## Bug 1: `/status` reads cwd from the process working directory

`bridge/status.clj:80`:

```clojure
:cwd  (System/getProperty "user.dir")
```

`/status` always reports `(System/getProperty "user.dir")` — wherever `bb` was launched from. Running `/cwd /some/path` updates the session correctly (verified by the `session_info` tool returning the new cwd), but `/status` keeps showing the JVM cwd until restart.

**Observed:** Three consecutive `/status` calls on Marvin after `/cwd` all reported `/Users/zane/Projects/isaac/isaac-live` (the worktree the server was launched from), while `session_info` reported the actual session cwd `/Users/zane/Projects/isaac/isaac-marvin`.

**Fix:** read `:cwd` from the session entry. `(or (:cwd entry) (System/getProperty "user.dir"))` — the fallback covers sessions that pre-date the field.

## Bug 2: `session_info` can't resolve model when session `:model` holds the upstream name

`tool/session.clj:19-25`:

```clojure
(defn- resolve-model-alias [session crew-cfg defaults]
  (model-name (or (:model session) (:model crew-cfg) (:model defaults))))

(defn- build-session-state [session model-alias cfg]
  (let [models    (or (:models cfg) {})
        model-cfg (get models model-alias)
        ...]))
```

`/model gpt` writes the session's `:model` to the **upstream model name** (`"gpt-5.4"`), not the alias (`"gpt"`):

```clojure
;; slash/builtin.clj:47
(store/update-session! ... {:model    (:model model-cfg)
                            :provider (:provider model-cfg)})
```

Then `session_info` looks up that string in `(:models cfg)` — which is **keyed by alias**, not by upstream name. The lookup misses. Cascade:

- `model.upstream` → `nil` (was `(:model model-cfg)` of a nil cfg)
- `provider` → `""` (no model-cfg, no provider name)
- `context.window` → `nil` (no model-cfg, no window)

**Observed:** Marvin's session_info reported `{"provider": "", "context": {"window": null}, "model": {"alias": "gpt-5.4", "upstream": null}}` — the alias and upstream are flipped, provider is empty, window is null. All four fields wrong from one missed lookup.

**Fix:** alias-key lookup first, upstream-match fallback second. If `(get models lookup-key)` misses, scan models for an entry whose `:model` equals `lookup-key`. Recover the alias (entry key), upstream, provider, and context-window from that entry. Existing alias-keyed callers are unaffected.

## Acceptance Criteria

- `/status` displays the session's `:cwd`, not the process working directory.
- After `/cwd /X`, the next `/status` on the same session shows `/X`.
- `session_info` reports correct `model.alias`, `model.upstream`, `provider`, and `context.window` when the session's `:model` is the upstream name written by `/model`.
- When the session's `:model` is itself an alias (the well-behaved case), `session_info` behavior is unchanged.

## Scenarios

In `features/bridge/commands.feature`:

```gherkin
Scenario: /status shows the session's cwd, not the process working directory
  Given the following sessions exist:
    | name       | crew | cwd                    |
    | cwd-status | main | /tmp/isaac-cwd-fixture |
  When the user sends "/status" on session "cwd-status"
  Then the reply matches:
    | pattern                       |
    | CWD .* /tmp/isaac-cwd-fixture |
```

In `features/tools/session_info.feature`:

```gherkin
Scenario: session_info resolves model when session :model holds the upstream name
  Given the isaac file "config/providers/hieronymus.edn" exists with:
    """
    {:api "grover" :auth "none"}
    """
  And the isaac file "config/models/lettuce.edn" exists with:
    """
    {:model "lettuce-grande" :provider :hieronymus :context-window 128000}
    """
  And the following sessions exist:
    | name       | crew | model          |
    | salad-bowl | main | lettuce-grande |
  And the current session is "salad-bowl"
  When the tool "session_info" is called
  Then the tool result is not an error
  And the tool result JSON has:
    | path           | value          |
    | model.alias    | lettuce        |
    | model.upstream | lettuce-grande |
    | provider       | hieronymus     |
    | context.window | 128000         |
```

**Both scenarios use only existing steps. Zero new steps.**

## TODO

- [ ] `bridge/status.clj`: change `:cwd` to read from the session entry, with `(System/getProperty "user.dir")` as fallback for older sessions
- [ ] `tool/session.clj`: alias-first / upstream-fallback model-cfg lookup in `build-session-state`
- [ ] Scenario in `features/bridge/commands.feature` (existing /status scenario at line 19 checks for a CWD line but doesn't assert the value — that's why this slipped)
- [ ] Scenario in `features/tools/session_info.feature`

## Discovery context

Both bugs surfaced 2026-05-14 while testing `/cwd` after we patched Marvin's `tidy-comet` session to swap `:provider "openai-codex"` for `"openai-chatgpt"`. The session has `:model "gpt-5.4"` (upstream name, written by `/model` at some earlier point) and was running fine for chat — only the readouts were broken.

Related: `[[isaac-29y5]]` (hook session cwd default) — different bug, same theme of "sessions store one thing, code reads it wrong."
