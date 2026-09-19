---
# isaac-okw1
title: 'Repin isaac-agent to main in isaac-discord and isaac-mcp (CI red: stale agent pin requires deleted config-cache; unreachable foundation via agent)'
status: todo
type: task
priority: high
tags:
    - ci
    - pins
created_at: 2026-09-19T03:38:55Z
updated_at: 2026-09-19T03:39:30Z
---

## Why

CI Tests on `main` is red in both repos for pin drift, not code:

| repo | pinned agent | symptom |
|---|---|---|
| isaac-discord | `8aecfc3` | `agent_steps.clj` requires `isaac.startup.config-cache`, deleted from foundation by isaac-f21o (09-15); foundation is pinned at `0b120cc` (post-f21o) so the namespace is gone → `FileNotFoundException` while loading `isaac.config.agent-steps` |
| isaac-mcp | `c827f23` | that agent's deps.edn pins foundation `1c8e45b`, reachable only from the squashed `bean/isaac-t1om` → `Error building classpath. Commit not found` |

isaac-lsz2 repinned foundation fleet-wide but left agent pins behind; isaac-agent main (`76320fa`) dropped the config-cache require (isaac-kk0o) and pins foundation main `df64bf1`. isaac-j4jr adds `bb lint-pins` so the unreachable case cannot recur; the stale-but-reachable case is what this bean fixes by hand.

## Do (each repo, one commit, worktree from origin/main — never the shared checkout)

- `deps.edn` and `bb.edn`: isaac-agent + isaac-agent-spec → `76320fab5d2c2ca614baa10a4a37d5fdec336e0c`; isaac-foundation, foundation-spec, foundation-test-support, marigold.* → `df64bf15c739165c496cbf81e3d922ad3aa3346f` (the sha agent main pins, so one foundation on the classpath). Any isaac-http pin → http main `d082206fa2a2e0099a892af863cb39016b7f3acd` only if the repo pins http at all.
- Pin rule (lsz2 §4): every isaac-* sha must be reachable from that repo's `origin/main` (`git merge-base --is-ancestor <sha> origin/main`).
- Fix whatever the newer agent/foundation surfaces in the repo's own specs/features; no feature-file edits beyond what the bean authorizes (`## Exceptions` below is empty on purpose — ask via the bean if a scenario must change).
- Bump each repo's version.

## Acceptance

```
cd isaac-discord && ISAAC_GIT=1 bb ci
cd isaac-mcp     && ISAAC_GIT=1 bb ci
```

CI Tests green on `main` for both repos after landing (link the runs in the bean). Order: mcp first (pure classpath fix), then discord.

## Exceptions

(none)
