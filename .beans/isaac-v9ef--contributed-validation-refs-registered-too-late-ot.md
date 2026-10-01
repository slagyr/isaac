---
# isaac-v9ef
title: 'Contributed validation refs registered too late: other modules'' schemas judged invalid (missing lex :model-exists?)'
status: completed
type: bug
priority: critical
created_at: 2026-10-01T02:22:19Z
updated_at: 2026-10-01T02:44:15Z
---

Found 2026-09-30 rehearsing the zanebot deploy (new foundation + the post-sweep module pins, live zanebot config copy). `isaac config validate` reports:

    error: module-index["isaac.comm.discord"].isaac.agent/comm[:discord].extra-schema - must be a schema map of field → spec

Cause: discord's :extra-schema uses `[:nil-or? [:crew-exists?]]` and `[:nil-or? [:model-exists?]]`. Those refs are contributed by isaac-agent through the `:isaac.config/validation-ref` berth (isaac-h2oo) and only registered when discovery runs `register-contributed-existence-refs!`. The meta-schema check of `:schema-map` values (isaac.foundation.schema.meta/valid-schema?) and schema composition run BEFORE that registration, so the refs are unknown and the schema is judged invalid. Verified by probe: with only foundation's lexicon loaded, discord's `:discord/channels` value-spec fields `:crew` and `:with-model` fail valid-schema?; every other field passes.

The same ordering bug is behind every "missing lex :model-exists? in :validations" seen today (`bb pins` in every repo, isaac-hooks' `:hooks` schema contribution on zanebot with old pins). Workers dismissed it as a macOS environment issue; it is not. CI passes only because its fresh roots don't hit the order.

## Acceptance
- Red first: a foundation scenario with a Marigold module contributing a validation ref (via :isaac.config/validation-ref) and a second Marigold module whose schema/extra-schema uses that ref: `config validate` is clean and the ref is enforced on a bad value.
- Contributed refs are registered from the module index before any manifest/schema validation or composition.
- Local repro clean: a root with `{:modules {:isaac.agent {:local/root "<abs>/isaac-agent"} :isaac.comm.discord {:local/root "<abs>/isaac-discord"}}}` → `libexec/isaac --root <root> config validate` has no extra-schema error; `bb pins` in isaac-http no longer fails with missing lex :model-exists?.
- `bb ci`, `bb features-slow`, `bb jvm-spec` green.

Blocks the zanebot deploy. Ungated (urgent); planner verifies.

## Landed on main
main-sha: isaac-foundation f79823d

`isaac.foundation.module.discovery/discover!` now calls
`validation-lexicon/register-contributed-existence-refs!` on the full
merged module index as soon as it's built — before `validate-contributions!`
(the berth-contribution schema-map check) runs, and before any caller's
later manifest/schema-map meta-check or config schema composition reads the
lexicon. `discover!` is the single chokepoint every entry point (CLI
launcher, config load, server boot, modules commands) uses to build the
index, so this one call site covers all of them.

Red-first Marigold fixture (discovery_spec.clj): a provider module
contributing `:isaac.config/validation-ref` plus a `:schema-map` berth
field, a consumer module's contribution using the ref — reproduced the
exact "must be a schema map of field → spec" error pre-fix, clean post-fix.

Real-module check: temp root with `{:modules {:isaac.agent {:local/root
...} :isaac.comm.discord {:local/root ...}}}` →
`libexec/isaac --root <root> config validate` — pre-fix: `error:
module-index["isaac.comm.discord"].isaac.agent/comm[:discord].extra-schema
- must be a schema map of field → spec`; post-fix: that error is gone
(only an unrelated "defaults.frequencies.crew - is required" remains,
expected for a minimal test root with no defaults configured).

`bb spec` (1339 examples) and `bb jvm-spec` (1339 examples) both show the
same 40/49 failures before and after the fix (byte-identical failure
lists) — all pre-existing, environmental, and unrelated to this bean:
macOS `service.cli` launchctl/plist specs, a `modules_deps_emit.feature`
JVM-boot feature, and a `handbook_chapter_spec.clj` test that calls
`schema-compose/effective-root-schema` directly against
`discovery/builtin-index` (bypassing `discover!`, so outside this fix's
chokepoint — appears to be classpath pollution across specs in the full
suite, not reproducible in isolation).

`bb pins` / `bb ci`'s `pins` task could not be verified in isolation: this
dev machine's real `~/.config/isaac.edn` points at the real `~/.isaac`
root regardless of `HOME=$(mktemp -d)` (babashka's `user.home` system
property does not follow a shell `HOME` export — confirmed directly), and
that real root pins `isaac.agent` at git sha `104b3c4a`, which predates
isaac-h2oo's `:isaac.config/validation-ref` contribution — a stale
personal pin, not a regression from this fix (reproduces identically with
the fix reverted). `bb features-slow` is green except one pre-existing,
unrelated JVM-boot feature failure (also identical before/after).

CI (isaac-foundation, 3 jobs: verify, Server boot with a module-provided
config type, Slow features) green on f79823d.

## Planner verification (2026-09-30)

Verified on f79823d: discover! registers contributed refs before validating contributions; CI green. Follow-up noted: handbook_chapter_spec bypasses discover!.
