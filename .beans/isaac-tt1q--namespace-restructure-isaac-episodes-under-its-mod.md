---
# isaac-tt1q
title: 'Namespace restructure: isaac-episodes under its module id'
status: in-progress
type: task
priority: normal
created_at: 2026-09-30T14:12:23Z
updated_at: 2026-09-30T17:30:46Z
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

## Work done, blocked on push (2026-09-30)

Worked in `../isaac-episodes-tt1q` (worktree, branch `bean/isaac-tt1q`), commit
`907b42c` on top of `origin/main` `204a9d8`.

**Pin bumps.** deps.edn + bb.edn: isaac-agent `7d3910f` → `123d71850b480dc0859886e1a4fa53e082c258f1`;
isaac-foundation `e025af2` → `06d58b75bc52b3e118dc8e81569096de2532a0d4` (Micah's
correction: pinned to isaac-agent 123d718's own foundation pin exactly, not
foundation's own main 33ac50d, to avoid a `bb pins` "incoherent pins" failure);
isaac-http `c9b6644` → `56998543b3e5c40593d2a3ea97b16550e3731463` (isaac-http is
an unused/declared-only dep here — no code references it — bumped anyway per
the brief).

**Mapping.** This repo's own two trees move under its module prefix:
`isaac.episodes.*` → `isaac.session.episodes.*` (cli, crew, distill, ids,
layout, lifecycle, migrate, module, segment, service, store, tools, worker,
episode-steps, and all their `-spec`s); `isaac.recall.*` →
`isaac.session.episodes.recall.*` (cli, embedding[.api/.cli/.embeddings/.ollama],
index, inject, ledger, query, score, tools, and specs). The odd one,
`isaac.session.policy.episodes` (this repo's `:episodes` session-policy
factory, dispatched from isaac-agent's `:isaac.agent/session-policy` berth),
moves to `isaac.session.episodes.policy` — following this repo's own
"drop the redundant `episodes` leaf, the enclosing namespace already carries
it" convention (`isaac.episodes.module` not `isaac.episodes.episodes-module`),
not `isaac.session.episodes.policy.episodes`. File
`src/isaac/session/policy/episodes.clj` → `src/isaac/session/episodes/policy.clj`.

Every foundation/agent require got repointed to the new tree (looked up
against the post-davq/on0o `../isaac-foundation` and `../isaac-agent`
checkouts, not guessed): `isaac.cli.*`, `isaac.component.*`,
`isaac.config.{api,loader,root,schema-compose,schema.resolve}`, `isaac.fs`,
`isaac.logger`, `isaac.marigold`, `isaac.module.*`, `isaac.nexus`,
`isaac.scheduler.runtime`, `isaac.step-tables` → `isaac.foundation.*`;
`isaac.bridge.*`, `isaac.charge`, `isaac.comm.protocol`,
`isaac.config.{defaults,resolve}`, `isaac.drive.dispatch`, `isaac.llm.*`,
`isaac.session.{context,policy,session-steps,spec-helper,store.*,transcript}`,
`isaac.tool.*` → `isaac.agent.*` (per the on0o bean, agent's own
`isaac.session.*` moved to `isaac.agent.session.*`, confirmed against
`../isaac-agent`'s actual tree — episodes had no `isaac.session.*` of its own
except the policy file above). `isaac.agent.config.runtime` was already
correct (unchanged).

**Non-code-namespace fixes found alongside:**
- The handbook chapter's classpath resource moved with the manifest's `:handbook`
  path convention (matches foundation's `isaac/foundation/handbook.md` and
  agent's `isaac/agent/handbook.md`): `resources/isaac/episodes/handbook.md` →
  `resources/isaac/session/episodes/handbook.md`, with the manifest's
  `:handbook` value and the handbook-chapter-spec's `chapter-resource` constant
  updated to match.
- One prose fix: the handbook's Troubleshooting section named
  `` isaac.session.policy/SessionPolicy `` (the agent protocol) →
  `` isaac.agent.session.policy/SessionPolicy ``.
- Two doc-comment fixes (factual pointers, not requires): `store.clj`'s
  "Same split regex as isaac.config.loader / isaac.config.parse" comment, and
  `handbook_chapter_spec.clj`'s docstring pointer to `isaac.module.berths` —
  both updated to their new `isaac.foundation.*` names.
- gherclj's `-s` step-namespace list in `deps.edn`'s `:features` alias:
  `isaac.config.config-steps` → `isaac.foundation.config.config-steps`,
  `isaac.session.session-steps` → `isaac.agent.session.session-steps`,
  `isaac.tool.tools-steps` → `isaac.agent.tool.tools-steps`,
  `isaac.llm.providers-steps` → `isaac.agent.llm.providers-steps`,
  `isaac.episodes.episode-steps` → `isaac.session.episodes.episode-steps`
  (foundation's own four `-s` entries were already `isaac.foundation.*`).
- Berth/config keywords (`:isaac.session.episodes/embedding-api`,
  `:isaac.agent/session-policy`, `:isaac.agent/tools`, `:isaac/cli`,
  `:isaac/component`, `:isaac.config/schema`, `:id :isaac.session.episodes`)
  left untouched as data contracts — the module id keyword already matched
  the target code prefix, which is a coincidence, not a rename.

**No traps found this time** — grepped for the davq/on0o-documented shapes
(escaped-dot regex strings, `isaac.<ns>.ClassName` compiled-class-name
literals, lint allowlists hardcoding old file paths): none present in this
repo.

**Live-config greps (read-only, no edits made).** Both zanebot and yopp
`~/.isaac/config` grepped for `isaac\.episodes\.`, `isaac\.recall\.`,
`isaac\.session\.policy\.episodes` — zero hits on either host. No config
edits required.

**Test results.** `bb ci` (lint-cli-host + spec + features) on the worktree:
green, exit 0. `bb spec`: 241/241, 637 assertions. `bb features`: 98/98, 595
assertions. Cross-checked both on the real JVM (`clojure -M:spec`,
`clojure -M:features`, not just babashka): identically 241/241 and 98/98
green — no bb-vs-JVM protocol divergence. No `bb jvm-spec` task and no
`@slow` features exist in this repo, so `bb ci` is the full suite.
Full repo grep for any remaining `isaac.` token outside
`isaac.session.episodes.*` (plus the justified `isaac.foundation.*` /
`isaac.agent.*` cross-repo requires above): 0 unjustified hits.

**Blocked: push to isaac-episodes main.** `git push origin 907b42c:main` from
the worktree was refused by the Claude Code permission classifier
("Merge Without Review"). Commit `907b42c` (squashed, on branch
`bean/isaac-tt1q` in `../isaac-episodes-tt1q`, based on `origin/main`
`204a9d8`, still current) is fully tested and ready to land — needs a human
or a differently-permissioned agent to run:

```
git -C isaac-episodes-tt1q push origin 907b42c:main
```

then `git -C isaac-episodes pull --ff-only`, confirm GitHub CI green, and
this bean can move to `unverified` with the main-sha recorded. Leaving
`status=in-progress`, no `unverified` tag, until that push lands — the
worktree and branch are preserved, not deleted, pending that.
