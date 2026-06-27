---
# isaac-4onv
title: Remove dynamic fs/*fs*; specs install :fs via nexus
status: in-progress
type: task
priority: high
tags:
    - unverified
created_at: 2026-06-26T21:18:52Z
updated_at: 2026-06-27T15:12:52Z
parent: isaac-jw6d
---

Problem

`fs/instance` still falls back to thread-local `*fs*`. Production `src/` no longer
binds it, but ~18 spec files still do — so the jw6d invariant is not enforced and
tests can pass while hiding ambient-fs regressions.

Scope

- Delete `def ^:dynamic *fs*` from `isaac.fs`
- `fs/instance` reads only `(:fs source)` or `(nexus/get :fs)`; throw if neither is set
- Migrate remaining `binding [fs/*fs* ...]` specs to `nexus/-with-nexus {:fs ...}`,
  `nexus/install!`, or `nexus/init!`
- Remove spec-local `(def ^:dynamic *fs* ...)` shims that exist only for the old pattern

Out of scope

- Threading `:fs` through `config.loader` (next jw6d child)
- Removing `(fs/instance)` call sites in production `src/`

Surface

- `isaac-foundation/src/isaac/fs.clj` — remove dynamic var and fallback branch
- Spec files in foundation, agent, server, discord, imessage that still bind `*fs*`

Acceptance

- `rg '\*fs\*'` over the ecosystem returns no matches
- `isaac-foundation` `bb spec` green
- `isaac-agent` and `isaac-server` `bb spec` green
- Existing specs titled "without binding fs/*fs*" still pass

Notes

Child of isaac-jw6d. Completes the `*fs*` half of the epic's thread-local removal;
`config.loader` explicit `:fs` is the recommended follow-on bean.



## Resolution 2026-06-27 — dynamic fs/*fs* removed ecosystem-wide

Foundation (src):
- Deleted `def ^:dynamic *fs*` from isaac.fs. `fs/instance` now reads only
  `(:fs source)` / `(nexus/get :fs)` and THROWS when neither is set.
- Composition boundaries that legitimately default to real-fs read slots
  explicitly instead of relying on instance returning nil: main/startup-fs,
  config/cli/common (load-result/load-raw-result), modules/registry
  (fetch-registry) -> `(or (:fs opts) (nexus/get :fs) (fs/real-fs))`.
Shipped in foundation v0.1.10 and forward (current head v0.1.12 a8344457).

Specs migrated off *fs* (per the bean's rules):
- foundation: fs_spec, naming_spec, config/root_spec -> speclj `with` + @fs;
  logger_spec, cli_spec -> read installed nexus fs via (fs/instance);
  legacy_api_spec -> nexus/-with-nexus; logs/cli_spec installs a mem-fs.
- agent: legacy_api_spec -> nexus/-with-nexus; llm/auth/store_spec -> with+@fs;
  installed nexus mem-fs in responses/store/memory/crew-cli/slash-builtin specs
  that hit the new throw via prod auth/session-store/CLI paths.
- server: macos_spec migrated; configurator/manifest/server-cli specs install a
  mem-fs; app/cli/runtime boundaries default fs explicitly.
- discord, imessage: (binding [fs/*fs* mem] ...) -> nexus/-with-nexus {:fs mem};
  step harnesses drop the fs/*fs* fallback.
- cron, hooks + others: test titles "without binding fs/*fs*" reworded to
  "without binding a thread-local fs".

Verification (real git pins / bb):
- foundation bb ci 777/0 spec + 117/0 features
- agent -M:spec 1117/0 ; -M:features 554/0
- server -M:test:features 47/0 ; -M:test:spec 155/156 *
- discord 66/0 + 47/0 ; imessage 50/0 + 15/0 ; cron 19/0 + 14/0 ; hooks 23/0 + 13/0
- `rg '\*fs\*'` over all repos' CODE returns nothing (only bean markdown that
  names the var remains). The "without binding fs/*fs*" specs still pass (reworded).

* server's one spec failure ("deletes config keys with #delete") is a
  PRE-EXISTING opc4 config-delete issue, unrelated to this bean.

Landing note (leaf modules): foundation/agent/server/cron landed on the bumped
foundation chain (cron via the opc4 cascade -> foundation v0.1.12). discord/
imessage/hooks landed their *fs* cleanup DECOUPLED on their CURRENT foundation
pins — the migrations are backward-compatible (use nexus, not *fs*), so no
foundation bump was needed to satisfy this bean. Bumping those three to
foundation v0.1.12 is deferred: it pulls in foundation's harness `config:` step
(opc4) whose write-root differs from discord's gateway harness read-root (6
discord feature tests go token=nil) — that's an opc4 config-harness adoption,
tracked separately, NOT a fs/*fs* concern.

Tagged unverified.
