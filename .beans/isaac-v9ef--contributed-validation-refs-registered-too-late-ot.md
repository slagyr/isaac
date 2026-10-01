---
# isaac-v9ef
title: 'Contributed validation refs registered too late: other modules'' schemas judged invalid (missing lex :model-exists?)'
status: todo
type: bug
priority: critical
created_at: 2026-10-01T02:22:19Z
updated_at: 2026-10-01T02:22:19Z
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
