---
# isaac-67nr
title: "Migrate state-dir threading to (system/get :state-dir)"
status: scrapped
type: task
priority: normal
created_at: 2026-05-08T20:31:46Z
updated_at: 2026-05-09T15:15:01Z
---

## Description

state-dir is threaded as a parameter through 39 production source files (verified by grep). After this bead, callers read it from isaac.system instead of receiving it as an arg. Function signatures de-clutter substantially across the entire codebase.

The migration is large enough to warrant cluster breakdown — same pattern as isaac-o3da's caller migration. Each cluster lands as its own commit; one bead with checklist.

## Cluster breakdown

Each cluster removes the `state-dir` parameter from public/private fns in those files. Callers then drop the explicit pass-through. Internally, fns that need the path read `(system/get :state-dir)`.

### Pre-task: startup wiring

Before any cluster: at every top-level entry point (CLIs, server, cron, ACP server) construct/derive state-dir as today, then call `(system/register! :state-dir state-dir)` BEFORE invoking any subsystem.

Specs use `(system/with-system {:state-dir <test-dir>} ...)` to set up.

### A. Tools (~10 files, leaf — start here)
- src/isaac/tool/builtin.clj, exec.clj, file.clj, glob.clj, grep.clj (post isaac-zc91), memory.clj, session.clj, web_fetch.clj, web_search.clj, fs_bounds.clj
- Tools currently take state-dir to read config, locate sandboxed paths, etc. They become parameter-less for that concern.

### B. Drive
- src/isaac/drive/turn.clj
- src/isaac/drive/dispatch.clj

### C. Comm/ACP + delivery
- src/isaac/comm/acp/{cli,server,websocket}.clj
- src/isaac/comm/delivery/queue.clj

### D. Bridge
- src/isaac/bridge/{core,cancellation,status,chat_cli,prompt_cli}.clj

### E. Cron + server hooks
- src/isaac/cron/{scheduler,state}.clj
- src/isaac/server/{app,hooks}.clj

### F. Session + LLM auth
- src/isaac/session/{cli,compaction,store/file}.clj (post isaac-o3da)
- src/isaac/llm/api/openai/shared.clj
- src/isaac/llm/auth/* (post isaac-ut87)
- src/isaac/config/{loader,cli/common}.clj
- src/isaac/crew/cli.clj
- src/isaac/module/coords.clj
- src/isaac/home.clj
- src/isaac/api.clj
- src/isaac/main.clj — startup wiring

Recommended order: pre-task wiring → A → B → C → D → E → F. Leafy clusters first; subsystem-spanning ones last.

Test specs migrate alongside their src cluster; spec callers use `(system/with-system ...)` to provide a fresh state-dir per test.

## Why no new Gherkin scenarios

Pure refactor. External behavior unchanged. Existing feature/spec scenarios serve as regression tests throughout.

## Soft dependency on isaac-o3da

Cluster F includes session.* files. If isaac-o3da lands first, the SessionStore impl already encapsulates state-dir; this migration just removes the explicit threading from its callers. If this bead lands first, isaac-o3da's session migration cluster picks up fewer state-dir threading sites. Either order works; both flow through the same end state.

## Out of scope

- Migration of other registries (covered by adjacent beads).
- Renaming state-dir to something else (call it state-dir for now; bikeshed later).
- Bundling state-dir into a richer "paths" record (could be done later if useful).

## Acceptance Criteria

bb spec green throughout each cluster commit; bb features green throughout; no production function (outside startup wiring + test specs) takes state-dir as a parameter; (system/get :state-dir) is the canonical reader; main.clj and other entry points register :state-dir into isaac.system at startup before subsystem activation; spec/isaac/**/*.clj uses (system/with-system {:state-dir ...} ...) for setup.

