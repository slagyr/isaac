---
# isaac-87nv
title: 'Fleet pin sweep: every repo on foundation 98de59a+ and agent''s new main, coherent'
status: todo
type: task
priority: high
created_at: 2026-09-30T23:58:18Z
updated_at: 2026-09-30T23:58:18Z
---

Micah, 2026-09-30: after the namespace restructure (isaac-vyqs) leaves pin foundation 06d58b7 + agent f953042 (+ older sibling shas), while foundation main has moved to 98de59a (4eay, 82nx, 46ty, CI restore). `bb pins` requires each repo's direct pins to agree with what its pinned siblings themselves pin.

## Order (layers; each layer lands before the next starts)
1. isaac-agent: foundation (+ -spec, -test-support) → foundation main.
2. isaac-http: foundation → same sha, agent → agent's new main.
3. cron, episodes, google, cli-server, claude-code, discord, foreman, hail, hooks, imessage, worksite.
4. acp (episodes, http), gchat + gmail (google), handbook (cron).
5. mcp (acp), cli-proxy (acp, cli-server).
Each repo: every isaac-* pin → the sibling's current main, coherent with that sibling's own pins. isaac-server is a git mirror of isaac-http; skip it.

## Also, while touching them
- isaac-google: `isaac.google.steps` → `isaac.google.google-steps` (gherclj aliases step namespaces by last segment; bare `.steps` collides). Drop the explicit `-s isaac.google.steps` wherever the glob now matches (gchat, gmail).
- isaac-hooks: `isaac.hooks.steps` → `isaac.hooks.hooks-steps`; same cleanup.

## Acceptance
- Every repo's GitHub CI green on its new main, `bb pins` coherent in CI.
- A table in this bean listing each repo's final main sha and its foundation/agent pins.
Ungated; planner verifies.

## Layer 1-2

| repo | new main sha | foundation pin | agent pin |
|------|---------------|-----------------|-----------|
| isaac-agent | c81bf0920b65e1558ab2f06d7af055713632bef0 | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | n/a |
| isaac-http | 4fdd535bb7231fa18d7812c6f0d5b2d35fab740c | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | c81bf0920b65e1558ab2f06d7af055713632bef0 |

Both repos' GitHub CI green on the shas above. isaac-agent: `bb ci` (1840 spec examples / 819 feature examples, 0 failures) and `bb jvm-spec` (1840 examples, 0 failures) green on the first try with isolated HOME — the turn_store.feature timing flakes did not reproduce, so no rerun was needed. isaac-http: `bb spec`/`bb features`/`bb jvm-spec` all green (197/120/197 examples, 0 failures); `bb pins` fails locally on this machine (macOS) with an unrelated pre-existing error (`missing lex :model-exists? in :validations` composing the :crew schema when the standalone foundation CLI boots with no real `~/.isaac` root) — reproduced identically against the *old* pins and a from-scratch clone matching CI's exact layout, so it predates this bump and isn't platform-portable to CI; GitHub Actions (Linux) CI for both commits is green, `bb pins` included (silent/passing step).

## Layer 3

| repo | new main sha | foundation pin | agent pin | other pins |
|------|---------------|-----------------|-----------|------------|
| isaac-hooks | c54989825c39462c23d7dcbf3c6f5217a32b01eb | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | c81bf0920b65e1558ab2f06d7af055713632bef0 | n/a |
| isaac-imessage | c6adcd640bcdce7dc00ca0edefce0297adb7bb52 | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | c81bf0920b65e1558ab2f06d7af055713632bef0 | http 4fdd535bb7231fa18d7812c6f0d5b2d35fab740c |
| isaac-worksite | 9e6a0dff94776850147f552e50ed05f9d5c3efca | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | c81bf0920b65e1558ab2f06d7af055713632bef0 | http 4fdd535bb7231fa18d7812c6f0d5b2d35fab740c |
| isaac-cron | d8f3831 | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | c81bf0920b65e1558ab2f06d7af055713632bef0 | http 4fdd535bb7231fa18d7812c6f0d5b2d35fab740c |
| isaac-episodes | f0efa17 | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | c81bf0920b65e1558ab2f06d7af055713632bef0 | http 4fdd535bb7231fa18d7812c6f0d5b2d35fab740c |
| isaac-cli-server | a57a97a | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | n/a | http 4fdd535bb7231fa18d7812c6f0d5b2d35fab740c |
| isaac-claude-code | ad434cb | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | c81bf0920b65e1558ab2f06d7af055713632bef0 | http 4fdd535bb7231fa18d7812c6f0d5b2d35fab740c |
| isaac-google | 342da24 | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | c81bf0920b65e1558ab2f06d7af055713632bef0 | http 4fdd535bb7231fa18d7812c6f0d5b2d35fab740c |
| isaac-discord | f34ef3d | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | c81bf0920b65e1558ab2f06d7af055713632bef0 | http 4fdd535bb7231fa18d7812c6f0d5b2d35fab740c |
| isaac-foreman | eb0e5b3 | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | c81bf0920b65e1558ab2f06d7af055713632bef0 | http 4fdd535bb7231fa18d7812c6f0d5b2d35fab740c |
| isaac-hail | b3b6f13 | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | c81bf0920b65e1558ab2f06d7af055713632bef0 | http 4fdd535bb7231fa18d7812c6f0d5b2d35fab740c |

isaac-hooks, isaac-imessage, isaac-worksite: GitHub CI green on the shas above. All three `bb ci` green with isolated HOME (hooks 20/20 examples; imessage 76 spec + 23 feature examples; worksite 21 spec + 8 feature examples, 0 failures everywhere); `bb jvm-spec` also run and green on each. None of the three has a `bb pins` CI step. isaac-hooks also renamed `isaac.hooks.steps` → `isaac.hooks.hooks-steps` (feature-steps/isaac/hooks/steps.clj → hooks_steps.clj) in the same commit, updated the ns, `helper!`, and defgiven/defthen refs, and dropped the now-redundant explicit `-s isaac.hooks.steps` glob entry (and its stale comment) from deps.edn's `:features` main-opts.

isaac-cron, isaac-episodes, isaac-cli-server, isaac-claude-code: GitHub CI (`bb ci`, native) green on the shas above (cron 28+21 examples; episodes 98 examples; cli-server 20+20 examples; claude-code 98 spec + 67 feature examples; 0 failures everywhere). isaac-cli-server has no isaac-agent dependency, so no agent pin to bump. The worktree-add step was run as a sibling (`../`), not nested under the source repo, after a nested attempt broke isaac-cron's `:local/root "../isaac-agent/modules/isaac.comm.telly"` features dep (path resolves relative to deps.edn's directory). `bb jvm-spec` on isaac-cli-server and isaac-claude-code fails locally with a pre-existing, unrelated error (`clojure -M:spec` can't find `speclj/main` on the classpath via the `:spec {:alias :test …}` indirection) — reproduced identically on each repo's unmodified pre-bump main, and neither repo's CI runs `jvm-spec` (only `bb ci`), so it's out of scope here. isaac-claude-code's `bb jvm-features` passes its 67 examples with 0 failures but then hangs for the remaining ~50s until the test-support harness's 60s watchdog kills it (exit 124) — also reproduced identically on unmodified pre-bump main; `bb features` (native, what CI runs) completes cleanly in ~10s. None of the four has a `bb pins` CI step.

isaac-google, isaac-discord, isaac-foreman, isaac-hail (layer 3 group B): GitHub CI green on the shas above. `bb ci` green with isolated HOME on all four (google 268 spec + 44 feature examples; discord 56 native-spec + 109 jvm-spec + 68 feature examples; foreman 76 spec + 23 feature examples; hail 67 spec + 104 feature examples; 0 failures everywhere); `bb jvm-spec` also run and green on google and hail. isaac-discord's bb.edn auto-switches `:spec`/`:features` to `:dev-local:*` whenever `ISAAC_GIT` is unset and sibling `../isaac-agent`, `../isaac-foundation`, `../isaac-http` checkouts exist (true for this machine), so it was run with `ISAAC_GIT=1` locally to exercise the actual pinned shas, matching what its CI sets. isaac-hail's `:features` deps.edn alias always runs against sibling checkouts (no git/sha there at all) rather than git-pinning foundation/agent/http for features; its CI resolves the SHAs to pin from deps.edn's `:deps`/`:spec` entries and clones real `../isaac-foundation`, `../isaac-agent`, `../isaac-http` sibling checkouts at those exact SHAs before running `bb ci` — confirmed the local siblings here were already sitting on the three target SHAs before running `bb ci` locally too. None of the four has a `bb pins` CI step. Per the isaac-cron precedent noted above, worktrees for discord/foreman/hail were added as true siblings (`../<repo>-87nv`); isaac-google's was added nested (inside `isaac-google/`, as the brief's literal command produces) — harmless there since isaac-google's `bb ci` doesn't exercise its `:dev-local` alias, but worth flagging as a brief wording trap for the next group.

isaac-google also renamed `isaac.google.steps` → `isaac.google.google-steps` (feature-steps/isaac/google/steps.clj → google_steps.clj), updated the ns/`helper!`/self-references, and dropped the now-redundant explicit `-s isaac.google.steps` entry from deps.edn's `:features` main-opts (glob now matches). It also hit one real pin-caused break: isaac-http's new main dropped `audit.clj`'s inline `(or (get-in cfg [:http :oidc :jwks-alert-threshold]) 1)` fallback in favor of the schema-level default alone; isaac-google's `push_door.feature` background never declares an `:http` config section at all (not needed for any of its OTHER oidc checks, which don't read top-level `:http` config), so `conform-berth-slices` skips conforming that slice (it only conforms a berth whose top-level key is already non-nil in raw config) and the threshold comes back bare `nil`, which NPEs on `(>= count nil)` on the JWKS-unreachable path (500 instead of the expected 401-fail-closed). Fixed by adding `http.oidc.jwks-alert-threshold | 1` to the feature's Background config table rather than touching isaac-http (shared, already-landed, other layer-3 workers depend on its current sha) — confirmed this is a pin-only-exposed gap, not a logic change on isaac-google's side: `skew-s`/`cache-s` in isaac-http's `oidc.clj` still carry the same `(or ... default)` pattern, only this one function in `audit.clj` lost it.

## Layer 4-5 (acp chain)

| repo | new main sha | foundation pin | agent pin | other pins |
|------|---------------|-----------------|-----------|------------|
| isaac-acp | e0f9711b63a81cccd936bca16af405b70a37a421 | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | c81bf0920b65e1558ab2f06d7af055713632bef0 | http 4fdd535bb7231fa18d7812c6f0d5b2d35fab740c, episodes f0efa17f19e3685409697b528dc1c22e05cbcfa5 |
| isaac-mcp | a7c301e9cf9c462843b789db83b668b14edc7744 | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | c81bf0920b65e1558ab2f06d7af055713632bef0 | http 4fdd535bb7231fa18d7812c6f0d5b2d35fab740c, acp e0f9711b63a81cccd936bca16af405b70a37a421 |
| isaac-cli-proxy | 6676a8070092bfb75a09a8ae7fc99cd3e6bfd5b2 | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | c81bf0920b65e1558ab2f06d7af055713632bef0 | http 4fdd535bb7231fa18d7812c6f0d5b2d35fab740c, acp e0f9711b63a81cccd936bca16af405b70a37a421, cli-server a57a97ad253b0d3f9d09a7c71e48606445148c63 |
| isaac-handbook | 46f0b0dbcc5bd5350e4886dcefe351d63d64804b | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | c81bf0920b65e1558ab2f06d7af055713632bef0 | cron d8f383194a624668c0dcd20a067c4ee2f8d74343 |

isaac-acp: GitHub CI green. `bb ci` (native) green with isolated HOME (81 spec + 70 feature examples, 0 failures); `bb jvm-spec` (81 examples) and `bb jvm-features` (70 examples) also run and green. No `bb pins` CI step. Worktree added as a true sibling (`../isaac-acp-87nv`).

isaac-mcp: pinned acp to the isaac-acp sha just landed, plus foundation/agent/http to the fleet shas (all already-swept siblings). GitHub CI green. `bb ci` green (45 spec + 13 feature examples, 0 failures); `bb jvm-spec` also run and green. No `bb pins` CI step.

isaac-cli-proxy: pinned acp to the isaac-mcp-chain acp sha and cli-server to a57a97a, plus foundation/agent/http. Also updated two hard-coded `:git/sha` coords in `spec/isaac/cli_proxy/integration_steps.clj` (isaac-cli-server and isaac-acp coordinates used to spin up real subprocess fixtures for the integration features — brief named this file as `feature-steps/integration_steps.clj`, actual path is `spec/isaac/cli_proxy/integration_steps.clj`). GitHub CI green. `bb ci` green (27 spec + 29 feature + six repeated 6-example integration-feature runs, 0 failures throughout); `bb features-slow` also run and green (6 examples, 0 failures). `bb jvm-spec` fails locally with a pre-existing, unrelated error (`clojure -M:spec` can't find `speclj/main` via the `:spec {:alias :test …}` indirection) — reproduced identically on the unmodified pre-bump main, and CI only runs `bb ci` (native), so out of scope. No `bb pins` CI step.

isaac-handbook: pinned cron to d8f3831, plus foundation/agent (no isaac-http dependency here). Note: handbook's pre-sweep agent pin (`123d7185…`) was already stale relative to the fleet's new agent main (`c81bf09…`, itself an isaac-87nv pin commit) — bumped to current. GitHub CI green. `bb ci` green (42 spec + 22 feature examples via `clojure -M:spec`/`-M:features`, 0 failures) — handbook has no native bb spec/feature runner, `ci` delegates straight to JVM. No separate `jvm-spec`/`jvm-features` tasks and no `bb pins` CI step.
