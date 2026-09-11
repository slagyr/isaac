---
# isaac-sjir
title: 'Extract the file-backed session store into isaac-file-sessions: the agent knows only the memory store'
status: draft
type: feature
priority: normal
created_at: 2026-09-11T19:21:41Z
updated_at: 2026-09-11T19:21:41Z
---

Repos: new slagyr/isaac-file-sessions + isaac-agent + isaac-episodes + isaac registry. Named by Micah 2026-09-11. Same shape as isaac-209q: an extraction behind an existing seam (the session store SPI), moved scenarios stay byte-identical except namespaces; agent keeps the seam and a pure in-memory store.

## Scope (measured on agent main e9cba64; session/ is 6,576 lines)
Moves (~1,900 lines): store/impl_common (1,182: paths, EDNL read/write, rotation, sessions index, on-disk turn markers, migrations, torn-line repair), store/sidecar (196), migrate.clj (129, jsonl→ednl), the disk half of store/memory (~150: hydrate-from-disk + persist-when-rooted — chronicle behaviour wearing a memory costume; the agent's MemorySessionStore becomes pure), the file-talking parts of session/cli (~200: File column, segments, repair), bridge/resume's last direct marker-path use (~10; goes behind the SPI).
Stays in agent: SPI, policies (chronicle 45 lines is the default policy over ANY store), compaction, frequencies, schema, context, transcript.
Manifest: :isaac.agent/session-store {:file …} (the store factory berth already exists for the memory store — confirm/create), :isaac/cli contributions for sessions file commands, :isaac.config/check for layout checks.

## Decisions to confirm with Micah
1. Without this module an install has ephemeral sessions and blind CLI commands (each `isaac` command is its own process and today sees server state because the memory store hydrates from disk when rooted). Acceptable: the module is required for real deployments, like the server; the dev/test agent runs on memory.
2. isaac-episodes depends on isaac-file-sessions for the nested layout helpers (sessions/<crew>/<sid>/episodes/<eid>/), rather than a third shared layout library.
3. Feature steps that plant sessions on disk and assert on files move to the module's spec-support (agent-spec keeps memory-only steps); agent features run on the memory store.

## Acceptance (sketch until scenarios are planted)
- `grep -rn 'impl-common\|sidecar' src` in isaac-agent empty outside the SPI; agent bb spec && bb features green on the memory store.
- module bb spec && bb features green (moved scenarios byte-identical); isaac-episodes green against the module.
- Train: registry entry + `isaac modules install isaac.file-sessions` BEFORE the agent restart (sessions must resolve through the berth), rehearsed on a copy of the zanebot store first.

## Sequencing
After the current train (foundation 0.1.25 + server 0.1.15 on zanebot, isaac-kwhb, isaac-209q deploy) and before tools/provider-adapter extractions.
