---
# isaac-eqkb
title: Warm CLI via isaac-server in-process execution (daemon) — PARKED
status: draft
type: feature
priority: normal
created_at: 2026-07-13T17:29:33Z
updated_at: 2026-07-13T17:29:33Z
---

## Goal (PARKED — design captured 2026-07-13, not yet prioritized)

Erase the ~1.3s babashka source-load floor on every isaac CLI command by executing commands warm inside the already-running isaac-server, falling back to cold bb only when no current server is available. Addresses isaac-ogiu Hotspot #1 (the floor tki3/ogiu classpath caching CANNOT touch).

## Why this and not alternatives

- **Native-image / custom bb binary: ruled out.** GraalVM native-image needs a closed world at build time (no runtime require / dynamic classloading / eval). Isaac loads modules DYNAMICALLY at runtime via add-deps (the berth architecture). Native-image would force baking modules in at build time, abandoning the module system. Plus slow, memory-hungry, per-platform builds. bb was chosen precisely to keep dynamic loading — native fights the design.
- **Use the EXISTING isaac-server, not a new daemon.** It already has modules + config + command registry loaded warm. A new daemon re-pays all that. cli-server is already the entry module.

## Design

1. **In-process command dispatch in isaac-server**: invoke a command against the server's already-loaded registry and capture output, instead of the current cli-server behavior which SPAWNS `isaac <argv>` subprocesses (dispatch.clj:50-54 — pays full bb tax per command today). This in-process path is where ~2.3s -> ~sub-100ms lives.
2. **Thin non-bb client shim** as the local `isaac` entrypoint: ping the server socket in milliseconds (curl/shell), send the command if up, fall back to `bb isaac.bb` if down. The decision MUST NOT boot bb (that already loses the 1.3s). curl-to-localhost is ~ms.
3. **Staleness guard via basis check (reuses tki3's :basis)**: warm execution is valid ONLY when the server's loaded module-basis (foundation version + module SHAs) == current on-disk basis.
   - Match -> execute warm.
   - Mismatch (module upgraded / foundation bumped) -> cold-bb fallback (server is behind for its own orchestration too; signal 'restart pending').
   - **Config mtime EXEMPT** — config is hot-reloaded and live in the server, so warm reads are current (better than cold bb re-reading files). Only the un-hot-reloadable parts (module SHAs, foundation version) gate warm validity.
   By construction the daemon cannot return stale results — it self-demotes to cold exactly when its classpath is behind.

## Trade-offs to resolve at spec time

- **Isolation**: commands would run inside the orchestration server (crews/hail/discord). A pathological command could disturb it. Needs try/catch boundary + timeout; likely scope warm-eligibility to READS (mutating/long-running commands stay cold).
- Relationship to parked isaac-5zfv ('run as bb or jvm') — likely supersedes or absorbs it.

## Status

DRAFT / parked per Micah 2026-07-13 ('big and complicated... might be the right move, but for now just get the classpath cached'). Revisit after the low-hanging classpath cache (isaac-ogiu) ships and we see the residual floor in practice.
