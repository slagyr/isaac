---
# isaac-92p3
title: Launcher resolves modules per-module → multiple versions of foundation/modules on classpath
status: in-progress
type: bug
priority: high
tags: []
created_at: 2026-06-18T23:17:26Z
updated_at: 2026-06-18T23:34:45Z
---

CORRECTNESS BUG: the packaged launcher can put MULTIPLE versions of foundation
(and of any module) on one classpath -> nondeterministic first-wins loading.

## Root cause

compose-config-modules! (src/isaac/module/loader.clj:971) loops :modules and
calls add-module-deps! PER MODULE (loader.clj:154-174); each does a SEPARATE
babashka.deps/add-deps {:deps {lib coord}}. Every call resolves that module's
transitive deps INDEPENDENTLY and APPENDS to the classpath. So:
• each module pulls its OWN foundation via its deps.edn -> several foundation
  versions on the classpath, alongside the launcher's already-loaded bundled
  foundation. version.clj (version.clj:6-16) reads the FIRST isaac-manifest.edn
  with :id :isaac.foundation -> reports whichever wins.
• two modules sharing a dep (both pull isaac.agent) -> multiple agent versions.
• a module installed AND pulled transitively -> duplicate.

Observed: zanebot on packaged v0.1.1 reports `isaac 0.1.0` — its configured comm
modules drag foundation v0.1.0 onto the classpath, shadowing the bundled v0.1.1.
So `brew upgrade isaac` does NOT actually move the running foundation.

## Required invariant

Exactly ONE version of foundation, and exactly one version of EVERY module, on
the classpath. The installed SEED foundation is authoritative.

## Fix

1. SINGLE unified resolution: collect ALL valid :modules (id->coord) into ONE
   deps map, add-deps ONCE — tools.deps then computes a single basis (one
   version per lib, real conflict resolution) instead of N independent appends.
2. Seed foundation authoritative: EXCLUDE foundation from every module's
   transitive deps (:exclusions [io.github.slagyr/isaac-foundation], or
   :override-deps to the bundled local root) so the ONLY foundation is the
   installed seed. Modules still declare foundation for standalone dev; the
   launcher drops it because the seed provides it.
3. Result: `isaac --version` always reports the installed seed version
   regardless of module pins; no duplicate modules.

## Acceptance

• Two configured modules pinning DIFFERENT foundation shas -> exactly ONE
  foundation (the seed's) on the classpath; `isaac --version` == seed version.
• Two modules sharing a dependency -> that dep present once (single version).
• A module both explicitly installed and pulled transitively -> single entry.
• Verified on a real packaged install + zanebot's comm set.

## Relationships

• Root cause behind the post-v0.1.1-upgrade version skew.
• Substrate for isaac-0yp1 (transitive module deps must resolve in the SAME
  single basis) and isaac-iq1t (manifest scan). 0yp1's "scan all manifests"
  only makes sense once there's one coherent resolved classpath.
• Sibling of isaac-5h15 (coord consistency) — but this is the LOADER, not coords.

## Immediate workaround (zanebot, not a fix)

Re-install the comm modules so their coords pin foundation v0.1.1 consistently
(`isaac modules install isaac.comm.{discord,imessage,acp}`). Aligns the versions
so nothing skews, but does NOT remove the multi-version-on-classpath hazard.


## Update 2026-06-18 — 0yp1 landed ON TOP of this bug

isaac-0yp1 (completed) added transitive module DISCOVERY (resolve-deps! walks
manifests' :deps), but did NOT change the classpath-addition path:
add-module-deps! still does `bb-add-deps {:deps {lib coord}}` ONE COORD PER CALL
(loader.clj:182), now invoked for explicit AND every transitive module
(loader.clj:207, :1127). So the per-module-append hazard is now exercised MORE,
not less.

0yp1's tests do NOT cover this: all fixtures are :local/root marigold modules
(single foundation, no version conflict), so a green 0yp1 does not prove
version-safety. This bug (single unified resolution + seed-authoritative
foundation) is the missing substrate under 0yp1's transitive activation; until
it lands, transitive activation on a real VERSIONED module set has the same
multi-version skew that made zanebot report 0.1.0 on a v0.1.1 install.

Add an acceptance test with VERSIONED (git/sha) fixtures pinning different
foundation versions — the case the marigold :local/root fixtures can't reach.

## Verification failed

HEAD: 5454cee
Working tree: clean

The explicit-module batching is real, but the single-version invariant is still
false for transitive module discovery. `compose-config-modules!` now batches
configured modules in one `add-deps` pass, but `discover!` still resolves
transitive manifest deps through `resolve-manifest-resource` →
`ensure-module-deps!` one module at a time. That leaves duplicate module copies
on the runtime classpath when an explicit module is also pulled transitively.

Repro on fetched GitHub `isaac-foundation` `main` at `5454cee`:

- Focused specs are green: `env ISAAC_GIT=1 bb spec spec/isaac/module/loader_spec.clj spec/isaac/foundation/version_spec.clj` → `34 examples, 0 failures`.
- Current-zanebot-style comm set also reports the seed version correctly through the launcher: `./libexec/isaac --root <tmp-root> --version` → `isaac 0.1.2 (5454cee)`.
- But direct classpath inspection after a real compose + discover run with explicit `isaac.server` `ba30caa` plus `isaac.comm.acp` `d108562` shows duplicates:
  `{[:isaac.foundation "0.1.2"] 1, [:isaac.comm.acp "0.1.0"] 1, [:isaac.server "0.1.0"] 2, [:isaac.agent "0.1.0"] 1}`

That fails the bean's acceptance `A module both explicitly installed and pulled transitively -> single entry`, and it means the broader `exactly one version of EVERY module` invariant is still not enforced. The missing work is to bring transitive module resolution into the same unified basis instead of adding those coords one-by-one during discovery.
