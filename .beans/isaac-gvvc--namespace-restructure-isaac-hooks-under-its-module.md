---
# isaac-gvvc
title: 'Namespace restructure: isaac-hooks under its module id'
status: in-progress
type: task
priority: normal
created_at: 2026-09-30T14:12:23Z
updated_at: 2026-09-30T17:42:13Z
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

## Work done, not landed — push denied by classifier

Work done on `bean/isaac-gvvc` in a worktree of `isaac-hooks`
(`isaac-hooks-isaac-gvvc`), committed as one commit
(`c584193d9007362865baa9b11ee5c2cb4a74b649`), **not pushed** — both
`git push origin c584193...:main` and `git push origin bean/isaac-gvvc`
were denied by the Claude Code auto-mode classifier ("Out-of-Place
Publication"). Per the milestone brief's landing instructions I stopped
rather than work around it. The worktree and branch are left in place
(`/Users/micahmartin/agents/isaac/plan/isaac-hooks-isaac-gvvc`,
branch `bean/isaac-gvvc`) for Micah to push or for a resumed session with
push permission.

**Pin bump.** deps.edn + bb.edn: isaac-foundation `9f8a413` →
`06d58b75bc52b3e118dc8e81569096de2532a0d4` (the exact sha isaac-agent
123d718 itself pins); isaac-agent `4d08434` →
`123d71850b480dc0859886e1a4fa53e082c258f1`; isaac-http `c9b6644` →
`56998543b3e5c40593d2a3ea97b16550e3731463` (isaac-http's own isaac-fkqz
rename).

**Mapping.** isaac-hooks' own production code already lived at exactly
`isaac.hooks` (the module id itself, one file — the whole module) and
`isaac.hooks.handbook-chapter-spec` (already nested) — neither needed a
path/ns change; `spec/isaac/hooks_spec.clj` (ns `isaac.hooks-spec`)
likewise needed no move — it's the standard co-located `<ns>-spec`
speclj convention for testing a namespace of that exact name, the same
shape isaac-davq accepted for `isaac.foundation.module-spec` testing
`isaac.foundation.module`. The two feature-step files were the real
find: `feature-steps/isaac/hooks_steps.clj` (ns `isaac.hooks-steps`) and
`feature-steps/isaac/hooks_feature_bootstrap.clj` (ns
`isaac.hooks-feature-bootstrap`) were dash-joined siblings of the module
root, not nested under it — the exact shape isaac-tacl's planner
follow-up flagged as a leftover for `isaac.google-steps`. Moved to
`feature-steps/isaac/hooks/steps.clj` (`isaac.hooks.steps`) and
`feature-steps/isaac/hooks/feature_bootstrap.clj`
(`isaac.hooks.feature-bootstrap`), matching the isaac-google/isaac-discord
fix shape. Added an explicit `"-s" "isaac.hooks.steps"` entry to
`deps.edn`'s `:features` `main-opts` (the existing `"isaac.**-steps"` glob
only matches a `-steps`-suffixed final segment, so it doesn't pick up the
now-nested `isaac.hooks.steps` on its own — same fix isaac-tacl made) and
renamed the existing explicit `"isaac.hooks-feature-bootstrap"` entry to
`"isaac.hooks.feature-bootstrap"`.

Every foundation/agent/http require in `src/isaac/hooks.clj`,
`spec/isaac/hooks_spec.clj`, `spec/isaac/hooks/handbook_chapter_spec.clj`,
and the two moved feature-step files got the module prefix:
`isaac.bridge.core`, `isaac.charge`, `isaac.comm.null`,
`isaac.config.defaults`, `isaac.session.{context,store.spi,store.sidecar}`,
`isaac.prompt.template`, `isaac.spec-helper`, `isaac.llm.api.grover` →
`isaac.agent.*` (with `isaac.session.frequencies` → `isaac.agent.frequencies`,
matching isaac-on0o's explicit call, not `isaac.agent.session.frequencies`);
`isaac.config.{loader,runtime,api,configurator,schema-compose,schema.resolve}`,
`isaac.reconfigurable`, `isaac.fs`, `isaac.logger`, `isaac.nexus`,
`isaac.module.{protocol,loader,discovery}`, `isaac.marigold` →
`isaac.foundation.*`; `isaac.session.session-steps` →
`isaac.agent.session.session-steps`; `isaac.configurator-steps` →
`isaac.http.configurator-steps` (both quoted symbols in the feature
bootstrap's step-collision filtering, confirmed against the real
`isaac-http`/`isaac-agent` trees, not just pattern-matched). The manifest's
generic `:factory isaac.module.protocol/module` →
`isaac.foundation.module.protocol/module`. A stray docstring pointer in
`handbook_chapter_spec.clj` ("isaac.module.berths") → `isaac.foundation.module.berths`.

**Left alone (data, not code namespaces, per the bean):** `:isaac.config/schema`,
`:isaac.config/component`, `:isaac.hooks/hook`, `:isaac.http/route` berth
keywords; `isaac.comm.acp` (a different module's own namespace, referenced
only as a string literal in a negative-dependency test); `isaac.edn`
(config filename mentions).

**Live-config greps (read-only, no edits needed).** zanebot
(`ssh zane@zanebot.tail66e5f8.ts.net`) and yopp (`ssh yopp@yopp`)
`~/.isaac/config`: only `:isaac.hooks` module-id keyword hits on zanebot
(`modules.edn` and two dated backup snapshots of `isaac.edn`), no hits at
all on yopp. Checked every hook entity file under zanebot's
`~/.isaac/config/hooks/*.md` for a `:factory` frontmatter field (the one
way a live hook config could name a code symbol) — none set one. No
config edits required on either host.

**Test results** (worktree, `HOME=/tmp/isaac_scratch_home_gvvc`):
`bb config-bypass-lint` ok. `bb spec`: 36/36, 0 failures. `bb jvm-spec`:
36/36, 0 failures. `bb jvm-features`: 20/20, 0 failures (confirmed the
relocated `isaac.hooks.steps` namespace actually registers and is
exercised — `hot_reload.feature` is the one feature file using its
custom steps, "default Grover hook setup" / "the hook config path..." /
"the Isaac config harness is started" / "...registry entry has:", and it
passed). `bb ci` green end-to-end. No `bb pins`/`lint-pins` task exists
in this repo (foundation-only tooling, per isaac-z0c4's precedent). Full
grep of the tracked tree for any remaining pre-rename foundation/agent/http
namespace token: 0 unjustified hits.

**No GitHub CI run** — the branch was never pushed, so no PR/CI exists to
check.

**Next step for Micah or a resumed session:** `cd
/Users/micahmartin/agents/isaac/plan/isaac-hooks-isaac-gvvc && git push
origin c584193d9007362865baa9b11ee5c2cb4a74b649:main`, confirm GitHub CI
green, fast-forward the shared `isaac-hooks` checkout
(`git -C ../isaac-hooks pull --ff-only`), record the main-sha here, tag
this bean `unverified`, then remove the branch/worktree.
