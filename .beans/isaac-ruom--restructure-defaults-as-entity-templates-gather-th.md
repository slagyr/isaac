---
# isaac-ruom
title: Restructure :defaults as entity templates; gather the scattered defaults into it
status: in-progress
type: feature
priority: normal
created_at: 2026-09-21T16:10:51Z
updated_at: 2026-09-21T16:47:18Z
---

Repos: **isaac-agent** (schema in `resources/isaac-manifest.edn:531`, ~40
readers), **isaac-foundation** (`config/normalize.clj`, `config/loader.clj`).

## Why

`:defaults` is a flat map whose names say nothing about what they default:
`:model` is the model for crews that don't name one, `:effort` sits below
the provider layer, and `:provider-retry-after-ms` needs a prefix to say what
it belongs to. Other defaults are scattered outside it: root
`:tools :allow` / `:deny` / `:directories` / `:max-parallel` are really crew
defaults, and `:tools :defaults` is a second, unrelated "defaults" (tool output
caps).

## Structure

```clojure
:defaults
{:frequencies {:crew :main}                        ; crew selection; not an entity
 :crew        {:model        :default              ; was :defaults :model
               :context-mode :full
               :cycle        {…}
               :tools        {:allow […] :deny […] ; was root :tools :allow / :deny
                              :directories {…}     ; was root :tools :directories
                              :max-parallel 4}}    ; was root :tools :max-parallel
 :model       {}                                   ; :temperature (isaac-e2tb), :extra-system-prompt (isaac-5n68)
 :provider    {:effort                 7
               :history-retention      :retain
               :compaction             {…}
               :stream-idle-timeout-ms 300000
               :retry-after-ms         …           ; was :provider-retry-after-ms
               :auth-retry-ms          …}          ; was :provider-auth-retry-ms
 :tools       {:max-lines 400 :max-bytes 32768}}  ; was root :tools :defaults; nothing overrides these
```

There is no `:turn`, `:charge` or `:session` section. Every setting that would
go there already lives on crew, model or provider. Sessions only hold
overrides, which nobody configures in advance.

## The rule: a default is a template

`:defaults :<entity>` behaves exactly as if every entity of that kind had set
the field. So **where a default is written decides how it ranks.**
`:defaults :provider :effort 7` means providers get 7 unless they set their
own, and models and crews still outrank it, which is today's behavior.
`:defaults :crew :effort 7` would give every crew effort 7, and that would
outrank each model's effort. Both are legal. Document the rule prominently,
because it is the one surprising thing here.

To keep today's behavior, a default goes on the **lowest layer that carries
the field**.

## Schema

- `:crew`, `:model` and `:provider` are validated by the existing entity
  schemas. No separate list of default fields.
- `:frequencies` uses the frequencies schema, **selection fields only**. The
  `:with-*` fields are per-turn overrides at the top of the chain; a default
  carrying one would outrank everything.
- `:tools` gets a small schema (`:max-lines`, `:max-bytes`).
- **Provider schema additions.** `effort.clj` already reads
  `(:effort provider-cfg)`, but the provider schema has no `:effort`. Add it.
  Add `:retry-after-ms` and `:auth-retry-ms`, which also makes them
  overridable per provider.

## Behavior changes

- **Compaction merges key by key.** Today `:defaults :compaction` is
  all-or-nothing. It is skipped entirely when any higher layer sets any
  compaction key (`session/context.clj:153`, `higher-compaction-layers?`).
  Example: defaults `{:strategy :slinky :threshold 0.7}`, crew
  `{:threshold 0.9}`. Today that crew gets `:rubberband` (the code default),
  because the defaults were skipped. Under the template rule it gets
  `:slinky` with 0.9. The template behavior is the intended one.
- **An invalid `:defaults` must fail loudly.** Today `normalize-defaults`
  (`isaac-foundation/src/isaac/config/normalize.clj:37`) turns a `:defaults`
  that fails validation into `{}` silently, losing the default crew and model.
  Because there is no legacy support (below), an old-shape `:defaults` has to
  be a clear validation error that names the moved keys, not an empty map.

## Readers

About 40 places read these keys directly. `[:defaults :crew]` alone is read in
~15 (`charge.clj`, `bridge/core.clj`, `bridge/prompt_cli.clj`,
`tool/session.clj`, `tool/memory.clj`, `tool/fs_bounds.clj`,
`session/policy.clj`, `session/cli.clj`, `session/context.clj`,
`config/resolve.clj`, …), plus `provider_wall.clj`, `drive/turn.clj`
(cycle, max-parallel, caps), `tool/registry.clj`, `crew/cli.clj`,
`config/checks.clj`. Put each value behind one accessor while moving it, so
the next restructure touches one place.

## No legacy support (Micah, 2026-09-21)

No code reads the old shape, and nothing translates it. The loader knows only
the new structure. Installs are migrated by hand, using the strategy below.

## Migration strategy (documented here, applied by hand per install)

| old | new |
|---|---|
| `:defaults :crew` | `:defaults :frequencies :crew` |
| `:defaults :model` | `:defaults :crew :model` |
| `:defaults :context-mode` | `:defaults :crew :context-mode` |
| `:defaults :cycle` | `:defaults :crew :cycle` |
| `:defaults :effort` | `:defaults :provider :effort` |
| `:defaults :history-retention` | `:defaults :provider :history-retention` |
| `:defaults :compaction` | `:defaults :provider :compaction` (now merges; see above) |
| `:defaults :stream-idle-timeout-ms` | `:defaults :provider :stream-idle-timeout-ms` |
| `:defaults :provider-retry-after-ms` | `:defaults :provider :retry-after-ms` |
| `:defaults :provider-auth-retry-ms` | `:defaults :provider :auth-retry-ms` |
| `:tools :allow` / `:deny` / `:directories` / `:max-parallel` | `:defaults :crew :tools …` |
| `:tools :defaults` | `:defaults :tools` |

Procedure for each install:
1. Write the new `isaac.edn` beside the live one.
2. Validate it with the new build before swapping it in.
3. Upgrade the modules, swap the config and restart together. This is a
   module deploy, so a restart is expected; the no-restart rule is about
   config edits on a running server.
4. Verify a turn resolves the expected crew, model and effort.

Host-specific notes stay out of this public repo.

## Done when

- the new structure is the only one the loader accepts; the old shape is a
  validation error naming each moved key and its new path (spec)
- each section is validated by its entity's schema; `:frequencies` rejects
  `:with-*` (specs)
- template rule: a `:defaults :provider :effort` is outranked by model and
  crew effort; a `:defaults :crew :effort` outranks model effort (specs)
- compaction merges key by key (spec for the example above)
- provider schema has `:effort`, `:retry-after-ms` and `:auth-retry-ms`
- every reader goes through an accessor; no direct `[:defaults :crew]` reads
  remain
- `bb verify` and `bb jvm-spec` are both green
- zanebot migrated and verified by the procedure above; other installs done
  the same way

## Checkpoint (2026-09-21, scrapper@isaac-work-2)

Branches: `bean/isaac-ruom` pushed on **isaac-foundation** (5626c11) and
**isaac-agent** (f625fa7).

Done:
- foundation `schema-compose/resolve-entity-templates` expands
  `:entity-template {:kind … :except […] :override {…}}` markers in the
  `:defaults` schema against the composed entity schemas; a template never
  carries `:required?` or `:present?`.
- foundation `normalize-defaults` keeps an invalid `:defaults` as written
  (no more silent `{}`) and no longer injects code compaction defaults.
- agent manifest: `:defaults` is `{:frequencies :crew :model :provider :tools}`
  by template; every retired flat key (`:effort`, `:compaction`,
  `:provider-retry-after-ms`, root `:tools :allow/:deny/:directories/
  :max-parallel/:defaults`) carries `[:retired? "use <new path>"]`.
- provider schema gained `:effort`, `:retry-after-ms`, `:auth-retry-ms`.
- new `isaac.config.defaults` accessor ns; every reader in isaac-agent goes
  through it (no direct `[:defaults …]` reads remain in `src`).
- compaction now merges key by key through the template chain
  (`session/context.clj:151`).
- `bb spec` green in both repos (agent 1684 examples, foundation 1105).

Next:
- `bb features` in isaac-agent: `features/session/mutation.feature` and
  friends fail with "invalid configuration in /target/test-state" — the
  feature fixtures still write the flat `:defaults` shape somewhere the
  retired-key validation now rejects. Resume at
  `spec/isaac/session/session_steps.clj:213` (`stamp-fixture-default-crew`)
  and the feature `.feature` config tables.
- Downstream repos still read `[:defaults :crew]`/`[:defaults :model]`:
  isaac-episodes (lifecycle/cli/migrate/recall), isaac-hail (router.clj:261),
  plus their specs. Not yet touched.
- **isaac-agent `bb.edn` is temporarily pinned to `:local/root
  "../isaac-foundation"`** so the branch pair runs together. It must go back
  to a `:git/sha` naming the landed foundation main sha before landing.
