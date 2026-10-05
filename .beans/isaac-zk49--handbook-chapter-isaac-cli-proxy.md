---
# isaac-zk49
title: 'Handbook chapter: isaac-cli-proxy'
status: completed
type: task
priority: normal
created_at: 2026-09-30T04:56:37Z
updated_at: 2026-10-05T14:38:34Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-cli-proxy` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

- Cover the concepts THIS module owns (its config tables and keys, tools, comms, commands, behaviors), written for a MODEL operating Isaac, not a developer: what it is, how to change it with `handbook__configure` (config paths), how to verify, then a `### Troubleshooting` subsection under each concept. No source-code walkthroughs.
- Ground every claim in the module's code, manifest and feature scenarios. Mark anything you can't verify with `[verify]` for Micah.
- Refer to other modules' concepts by one line plus their topic id (e.g. crews → `isaac.agent`); don't re-document them. Use foundation's chapter (`isaac-foundation/src/isaac/foundation/handbook.md`) as the pattern and vocabulary.
- Schema `:description`s: any config key this module declares without a `:description` gets one (the config reference is generated from them).

## Acceptance

- Manifest `:handbook` names the chapter; loading config raises no "handbook ... not found" warning.
- A lint spec like foundation's `spec/isaac/foundation/handbook_chapter_spec.clj` (backticked `config:<path>` refs resolve in the composed schema; `isaac <command>` refs exist).
- The repo's full CI green; landed on main with a single squash commit.

## Ungated

Documentation plus a lint spec; no behavior change. Worker hands off `tag=unverified`; Micah reviews the chapter text.

## Landed on main

main-sha: isaac-cli-proxy 331170c

Added `:handbook "isaac/cli_proxy/handbook.md"` to `src/isaac-manifest.edn`,
the chapter itself at `src/isaac/cli_proxy/handbook.md`, and a lint spec at
`spec/isaac/cli_proxy/handbook_chapter_spec.clj`. No config schema keys are
owned by this module to add `:description` to — `:cli :remote` lives in the
operator's home pointer file (`~/.config/isaac.edn`), read before config
loads and never schema-checked (same as `:root`), so there's nothing there
for `handbook__configure`/the schema-generated reference to describe.

Chapter covers: the `:cli :remote` pointer-file setting and `isaac remote
use/off/status`; the explicit `isaac remote <url>/cli -- <command...>`
proxy and its exit-code passthrough; token resolution order
(`--token-file`/`--token-env`/`ISAAC_REMOTE_TOKEN`/pointer-file, deprecated
`--token`); and reconnect/stream-resume (`ISAAC_REMOTE_RECONNECT_SECS`,
ACP handshake replay). ~2,090 words.

`[verify]` flagged in the chapter itself: foundation's `isaac.main/run`
resolves the *implicit* remote-by-default routing seam via
`(requiring-resolve 'isaac.cli-proxy.client/run!)`, but this module's
source has no `isaac.cli-proxy.client` namespace as of this writing —
only `isaac.cli-proxy.cli` (the explicit `remote` command). Worth
confirming whether implicit routing is actually wired end-to-end, or
whether only the explicit `isaac remote` command currently works.

**Pin bump**: attempted foundation/agent/http → `origin/main` tips per the
bean (deps.edn + bb.edn). `bb spec`/`bb features` passed at the new pins,
but `bb features-slow` broke: `isaac-foundation-test-support`'s
`run-features-slow!` (at foundation `0c6e881`) now shells out to a
per-repo `bb -Sforce gherclj -t slow -t ~wip <location>` task instead of
calling `gherclj/-main` in-process, and this repo (like every sibling repo
checked — isaac-agent doesn't use this shared slow-runner path at all)
has no `gherclj` bb task. That's fallout beyond the bean's two named
upstream changes (agent isaac-e9jl `worker/await-idle!`, http isaac-q1iu
bearer adjudication), so per the bean's fallback instruction the bump was
reverted; `deps.edn`/`bb.edn` are unchanged from main. The lint spec reads
`:isaac/cli` straight off raw manifests instead of via
`isaac.module.berths/module-report`, which postdates the current
foundation pin (df64bf1) and isn't callable there.

One side effect of staying on the old pin: that foundation version
doesn't recognize `:handbook` as a known manifest key at all (added
later), so loading this module's manifest logs a `:manifest/warn
unknown-key :handbook` warning — not the "handbook ... not found"
warning the acceptance criterion names (that check doesn't exist yet at
this pin either), but worth knowing. Clears once foundation is bumped
past whatever adds `:handbook` support, which will need the `gherclj`
bb-task fallout above resolved first (or foundation/test-support pinned
to different shas — not attempted here).

**Local test evidence**: `bb spec`, `bb features`, `bb config-bypass-lint`,
`bb lint-cli-host` all green on `bean/isaac-zk49` before push.
`bb features-slow` has one locally-flaky scenario ("the server rejects a
remote command without a valid token", binding `0.0.0.0`) that also fails
on a pristine, untouched `main` checkout in this sandbox — pre-existing
local environment flakiness, not caused by this change. GitHub CI (`bb
ci`, includes `features-slow`) is green on the landed sha.
