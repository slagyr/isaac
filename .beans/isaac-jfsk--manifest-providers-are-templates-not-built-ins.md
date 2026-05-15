---
# isaac-jfsk
title: Manifest providers are templates, not built-ins
status: completed
type: task
priority: normal
created_at: 2026-05-15T15:46:49Z
updated_at: 2026-05-15T16:34:22Z
---

## Problem

`providers/lookup` in `src/isaac/llm/providers.clj` materializes a provider
config from the manifest catalog even when no user or module entry exists.
That breaks the intended mental model: **manifest provider entries are
templates, not built-in providers**. A real provider exists only when a user
config (`~/.isaac/config/providers/<name>.edn`) or module instantiates it.

The symptom that exposed this: on zanebot, Marvin's crew points at `:model
:gpt` → `:provider :openai-chatgpt`. There is no `openai-chatgpt.edn` in
`~/.isaac/config/providers/`, but `lookup` returned the manifest template
anyway. The system then proceeded into the oauth path and reported
`Missing OpenAI ChatGPT login. Run isaac auth login --provider openai-chatgpt`
even though `auth.json` had valid tokens. The real bug is that the provider
should not have resolved at all — Marvin should get a clean `unknown provider`
error pointing the user at the missing user config.

(Aside: the auth path itself also looks up the auth-store key via `:name`,
which the manifest template no longer carries. That hole still exists but is
moot once the resolver stops materializing templates.)

## Root cause

`resolve-provider*` at `src/isaac/llm/providers.clj:51`:

```clojure
entry (or user-entry module-entry built-in-entry)
```

When `user-entry` and `module-entry` are nil, it falls back to the manifest
template (`built-in-entry`) and returns it merged as if real.

## Proposed code change

In `resolve-provider*`, require a user or module entry at the top level;
keep allowing template fallback only when following a `:type` inheritance
chain. Add a separate `template` function for raw catalog access.

Sketch (single fn with a flag, no structural reshuffle):

```clojure
(defn- resolve-provider* [sources provider-name seen require-instance?]
  (let [provider-name (->id provider-name)]
    (when-not (contains? seen provider-name)
      (let [{:keys [built-ins modules users]} sources
            built-in-entry            (get built-ins provider-name)
            module-entry              (get modules provider-name)
            user-entry                (get users provider-name)
            entry                     (if require-instance?
                                        (or user-entry module-entry)
                                        (or user-entry module-entry built-in-entry))
            same-name-base            (when user-entry
                                        (merge built-in-entry module-entry))
            inherited-provider-name   (->id (or (:type entry) (:from entry)))
            inherited-provider-config (when inherited-provider-name
                                        (resolve-provider* sources inherited-provider-name (conj seen provider-name) false))]
        (when entry
          (merge (or inherited-provider-config same-name-base {})
                 (dissoc entry :type :from)))))))
```

Wire `lookup` to call with `require-instance? = true`. Add `template` as the
permissive 3-arity caller for raw catalog access. Update `grover-defaults`
to read from `template` (grover sim shouldn't require an instantiated upstream).

`known-providers` and `dispatch.clj`'s "did-you-mean" stay as-is: they
should keep listing manifest names for discoverability.

## Affected unit specs (~20)

Need to update:
- `spec/isaac/llm/providers_spec.clj` — the `describe "defaults"` block
  expects template config; rename to `describe "template"` and call
  `sut/template`. Keep one `defaults` test that asserts the new strict
  behavior (returns nil for manifest-only providers).
- `spec/isaac/llm/api_spec.clj` — `normalize-pair merges catalog defaults
  under user config for known providers` and `resolves grover:<target>...`
  tests need either an explicit user provider in cfg or to assert the new
  behavior.
- `spec/isaac/drive/dispatch_spec.clj` — the two `normalize-provider defaults`
  tests already use `with-redefs` against `openai-responses/chat` and pass
  raw config in; they should instantiate `openai-chatgpt` via cfg.
- `spec/isaac/bridge/prompt_cli_spec.clj:83` — alias-match grover case;
  instantiate grover in cfg.
- `spec/isaac/comm/acp/server_spec.clj` (one test, codex SSE) — needs
  openai-chatgpt instantiated in cfg.
- `spec/isaac/cli/chat_spec.clj` — three `run-turn!` tests; add provider
  instantiation in cfg.

## Affected gherkin scenarios

Will break (rely on auto-materialization via `default Grover setup`):
- `features/bridge/model.feature` — `:provider grover` and `:provider grok`,
  neither instantiated. Fix: add `providers/grover.edn` via the step (see
  below) AND a `providers/grok.edn` line in the Background, or a
  `Given the provider "grok" is configured with:` step.
- `features/cli/acp.feature` — `:provider grover` via `default Grover setup`.
- `features/context/compaction.feature`, `features/context/memory_flush.feature`
  — `:provider grover` via `default Grover setup`.
- Possibly a couple non-`@slow` scenarios in
  `features/providers/grok/auth.feature`,
  `features/providers/anthropic/auth_api_key.feature`,
  `features/providers/ollama/integration.feature` — verify whether they
  invoke the provider chat path; if so add an `is configured` step.

Easy general fix: update `write-grover-defaults!` in
`spec/isaac/features/steps/session.clj:419` to also write
`config/providers/grover.edn` (e.g. `{}` or `{:type :grover}`). That alone
unblocks every grover-direct scenario.

Stays fine without changes:
- All `features/providers/*/messaging.feature` and `openai/dispatch.feature`
  use `grover:X` form → reads via `template` through `grover-defaults`.
- `features/providers/openai/auth.feature`, `codex_auth.feature` — explicit
  `Given the provider "X" is configured with:` steps.
- `features/llm/effort.feature`, `features/config/hot_reload*.feature` —
  explicit `config/providers/grover.edn`.
- `features/cli/init.feature` — scaffolds a real `config/providers/ollama.edn`.

## Zanebot remediation (post-merge)

After the code change ships, Marvin needs an explicit provider
instantiation. Drop `~/.isaac/config/providers/openai-chatgpt.edn`
containing `{}` (or `{:type :openai-chatgpt}` for clarity) and the next
turn will resolve through to the oauth tokens already in `auth.json`.

## Out of scope

The auth-store lookup uses `:name` from the wire config to choose the
`auth.json` key. With the manifest template no longer carrying `:name`,
that field needs to be re-injected either by the manifest, by `normalize`,
or by reading it from the provider id. File a follow-up once this lands;
fix is one line in `api/normalize`.

## Tasks

- [x] Patch `resolve-provider*` to require user/module entry at top level
- [x] Add `template` fn; route `grover-defaults` through it
- [x] Update unit specs listed above
- [x] Update `write-grover-defaults!` to write `providers/grover.edn`
- [x] Update direct-provider gherkin features (bridge/model, others) to instantiate
- [x] `bb spec` green (and `bb features`)
- [ ] On zanebot, write `~/.isaac/config/providers/openai-chatgpt.edn` `{}` and verify Marvin

## Verification

- `bb spec`: 1630/0
- `bb features`: 604/0
- Out-of-scope follow-up shipped: `:name` field retired from provider configs ([91ab1057](https://github.com/slagyr/isaac/commit/91ab1057)).
- Gherkin Backgrounds for grok/anthropic/ollama auth + bridge/model now write a `providers/<name>.edn` so the in-feature config describes a state that would actually work end-to-end.
