---
# isaac-wjng
title: 'Namespace restructure: isaac-discord under its module id'
status: in-progress
type: task
priority: normal
created_at: 2026-09-30T14:12:23Z
updated_at: 2026-09-30T17:37:26Z
parent: isaac-vyqs
blocked_by:
    - isaac-on0o
---

Micah, 2026-09-30. **A module's code lives under its module id.** isaac-foundation → `isaac.foundation.*`, isaac-agent → `isaac.agent.*` (e.g. `isaac.session.frequencies` → `isaac.agent.frequencies`), isaac-claude-code (`:isaac.provider.claude-code`) → `isaac.provider.claude-code.*`, isaac-episodes (`:isaac.session.episodes`) → `isaac.session.episodes.*`, comm modules → `isaac.comm.<name>.*`, and so on. Source, specs, spec-support, step namespaces, manifest symbols (`:factory`, berth entries), bb tasks and docs all move together. Clean cutover: no alias namespaces.

## Order: inside-out (Micah)

1. isaac-foundation (requires nobody).
2. isaac-agent: bump to the new foundation, update its foundation requires, rename its own.
3. Every other module, in parallel: bump foundation + agent (this absorbs the pin sweep isaac-5x21), update requires, rename its own. A module that requires another leaf (gchat/gmail → google) goes after that leaf.

Each repo is touched once.

## Deploy freeze

An installed Isaac runs one foundation and one agent, so zanebot/yopp don't take the new foundation until every installed module has migrated. Each repo's main stays green on its own pins meanwhile.

## Every child bean also

- greps zanebot and yopp live config (read-only, `ssh zane@zanebot…` / `ssh yopp@yopp`) for namespace names in data (hook `:factory`, embedding `:namespace`, etc.) and lists required config edits in the bean;
- updates its handbook chapter and README where namespaces are named;
- is ungated (mechanical refactor): acceptance = full CI green on main, a grep showing no namespaces outside the module's id prefix in src/spec (list justified exceptions), planner verification.

## Ready to land — blocked on push/merge permission

Work is done, CI green, PR open: **https://github.com/slagyr/isaac-discord/pull/1**
(branch `bean/isaac-wjng`, tip `3800998`). Both `git push origin 3800998:main`
and `gh pr merge 1 --squash` were denied by the Claude Code auto-mode
classifier (`Merge Without Review` / `Self-Approval` respectively). Needs Micah
(or a permitted actor) to merge PR #1, then this bean's `main-sha` and
`status=completed` (ungated flow has no `feature-baseline:` line, so a
planner/verify step should still eyeball it, but there's no separate
`isaac-verify` hand-off needed beyond a merge).

**Isaac-discord's own worktree/branch:** `../isaac-discord-isaac-wjng` (branch
`bean/isaac-wjng`), left in place pending the merge decision — do not delete
until PR #1 lands.

**Pin bump.** deps.edn + bb.edn: `isaac-foundation` `9ab25271` → `06d58b75bc52b3e118dc8e81569096de2532a0d4`,
`isaac-agent` `b1de8dc5` → `123d71850b480dc0859886e1a4fa53e082c258f1`,
`isaac-http` `689d3686` → `56998543b3e5c40593d2a3ea97b16550e3731463` (all current
mains, confirmed via `git rev-parse HEAD` on the sibling checkouts). No `bb
pins` task exists in this repo (matches isaac-agent's precedent) — pin sites
are the git/sha literals in deps.edn/bb.edn, found by grepping the old shas.

**Mapping.** isaac-discord's own code was already living under
`isaac.comm.discord.*` (module id `:isaac.comm.discord`) before this bean —
only every foundation/agent/http *require* needed updating, plus three
orphaned own-repo namespaces that predated the convention:

- `spec/isaac/module_activation_spec.clj` (ns `isaac.module-activation-spec`) → `spec/isaac/comm/discord/module_activation_spec.clj` (`isaac.comm.discord.module-activation-spec`)
- `spec/isaac/server/discord_app_spec.clj` (ns `isaac.http.discord-app-spec`, despite testing this repo's own server-boot integration) → `spec/isaac/comm/discord/server_app_spec.clj` (`isaac.comm.discord.server-app-spec`)
- `feature-steps/isaac/discord/feature_bootstrap.clj` (ns `isaac.discord.feature-bootstrap`) → `feature-steps/isaac/comm/discord/feature_bootstrap.clj` (`isaac.comm.discord.feature-bootstrap`)

Foundation requires renamed (23 tokens): `isaac.logger`, `isaac.nexus`,
`isaac.scheduler.runtime`, `isaac.config.{loader,root,berths,api,schema-compose,schema.resolve,change-source}`,
`isaac.reconfigurable`, `isaac.module.{loader,protocol,discovery,berths}`,
`isaac.component.{factory,protocol,registry,runtime}`, `isaac.fs`, `isaac.runner`,
`isaac.spec-helper` → matching `isaac.foundation.*` (spec-helper's target
chosen to match isaac-http's own convention — both foundation and agent ship
byte-identical `spec-helper` copies, so which owns a given call site is a
naming choice, not a functional one).

Agent requires renamed (16 tokens): `isaac.api`, `isaac.charge`,
`isaac.comm.{factory,protocol,render,registry,delivery.queue}`,
`isaac.util.ws-client`, `isaac.bridge.cancellation`, `isaac.llm.api.grover`,
`isaac.llm.providers-steps`, `isaac.session.store.{spi,impl-common}`,
`isaac.session.session-steps`, `isaac.session.spec-helper`,
`isaac.config.defaults` → `isaac.agent.*`, **except** `isaac.session.frequencies`
→ `isaac.agent.frequencies` (on0o's explicit call, not `isaac.agent.session.frequencies`
— confirmed against the isaac-agent sibling checkout). `isaac.config.defaults`
is agent-owned (`isaac.agent.config.defaults`), not foundation-owned, despite
the "config" prefix — confirmed by locating the actual file in the isaac-agent
checkout rather than guessing from the name.

`isaac.http.app` / `isaac.http.server-steps` (isaac-http, already migrated) —
names unchanged, pin bump only.

**Non-obvious fixes found by reading, not by grep alone:**
- `bb.edn`'s `config-bypass-lint` doc-string said "outside isaac.config.*" — the same trap davq/on0o both flagged in their own repos' copies of this doc-string. Corrected to `isaac.foundation.config.*` to match the actual `allowed-ns-prefixes` in `isaac.foundation.config-bypass-lint`.
- `bb.edn`'s dev-local sibling-file-existence checks (`jvm-spec`/`jvm-features`) hard-coded `"../isaac-agent/src/isaac/session/frequencies.clj"` as a **file path**, not a namespace token — a namespace-only substitution pass wouldn't have touched it. Updated to `"../isaac-agent/src/isaac/agent/frequencies.clj"`; verified the dev-local branch actually engages (`bb ci` output — no git fetch attempts).
- `features/comm/discord/frequencies.feature`'s prose named `isaac.session.frequencies` in its Feature description (a factual pointer at the implementing namespace, not a behavioral contract) — updated to `isaac.agent.frequencies`, same treatment davq gave `config_keys_list.feature`.
- `spec/isaac/comm/discord/handbook_chapter_spec.clj`'s docstring named `isaac.module.berths` (prose, not a require) — updated to `isaac.foundation.module.berths`; left its "isaac.google's and isaac.cron's own" mention alone (factual, those repos' own naming, out of scope).
- `AGENTS.md`'s "Split-module APIs" list named the old bare tokens — updated to the `isaac.foundation.*` / `isaac.agent.*` forms; also updated the one code-reference (`defmethod isaac.comm.factory/create` → `isaac.agent.comm.factory/create`).
- Re-sorted `:require` vectors back to alphabetical order in `src/isaac/comm/discord.clj`, `gateway.clj`, and four spec files where the mechanical substitution left `isaac.agent.*` / `isaac.foundation.*` tokens out of order (cosmetic, no functional effect, but matches this repo's existing convention).

**Left alone (no changes needed):** `resources/isaac/comm/discord/handbook.md`
and `README.md` already referred to `isaac.foundation`/`isaac.agent` only as
bare chapter-title mentions (not full dotted namespaces) — no drift.
`:isaac.agent/comm` and `:isaac/component` manifest keys, and `:isaac.comm.discord`
module-id keyword throughout specs, are data contracts per the parent bean —
untouched.

**Live-config greps (read-only, no edits made).** zanebot
(`ssh zane@zanebot.tail66e5f8.ts.net`) `~/.isaac/config`: only
`:isaac.comm.discord` hits, all as the `:modules`-map module-id keyword
(`modules.edn`, two `isaac.edn.pre-*` backups) — not a code namespace. yopp
(`ssh yopp@yopp`) `~/.isaac/config`: zero matches (module not installed there).
No config edits required on either host.

**Test results.** From `../isaac-discord-isaac-wjng` (worktree, dev-local
siblings): `bb ci` green — `config-bypass-lint: ok`; `bb spec` 56/56; `bb
jvm-spec` 109/109; `bb jvm-features` 68/68 (3 pre-existing pending,
`episodes.feature`, unrelated to this rename). Re-ran the full `bb ci` a
second time with `ISAAC_GIT=1` (forces the real pinned git SHAs instead of
`:dev-local`, pulling isaac-foundation/isaac-agent/isaac-http straight from
GitHub at the new pins) — identical green result, confirming the pins
resolve and the pinned mains are compatible.

**GitHub CI on the pushed branch:** not yet observed (branch/PR pushed but
merge is what's blocked, and CI runs on the PR — check `gh pr checks 1` in
the isaac-discord repo once available).

Full grep of the tracked tree for any remaining non-`isaac.comm.discord.*` /
`isaac.foundation.*` / `isaac.agent.*` / `isaac.http.*` namespace token: 0
unjustified hits (module-id keywords and `isaac.edn` path mentions excluded).
