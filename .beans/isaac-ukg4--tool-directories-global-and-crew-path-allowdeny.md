---
# isaac-ukg4
title: 'Tool directories: global and crew path allow/deny'
status: completed
type: feature
priority: high
created_at: 2026-08-21T22:20:00Z
updated_at: 2026-09-08T15:32:53Z
blocked_by:
    - isaac-ek0r
    - isaac-da0r
---

Path permission is a yes/no per (crew, path). Closed default: no grants,
no filesystem. Global policy, crew overlay. Hierarchical: longest
matching prefix wins. Same-length prefix uses the same cascade as tools
(crew after global).

Likely repo: **isaac-agent**. Needs **isaac-ek0r** (namespaced `fs/*`)
and **isaac-da0r** (global/crew `:tools` overlay). Does not block MCP.

## Decisions (2026-08-21, Micah)

- **Empty = deny all paths.** `fs/read` of anything fails until a grant
  matches.
- **Two keys, both layers:** `:tools :directories {:allow … :deny …}` on
  root and on crew, beside tool `:allow`/`:deny`.
- **Tokens:** `:cwd` (session workdir and descendants), `:quarters` (that
  crew’s area and descendants), absolute paths (that root and descendants).
  No implicit grants — recommended ship policy is explicit
  `{:allow [:cwd :quarters]}`.
- **Match:** path allowed iff under an allow prefix and no **longer**
  deny prefix applies. Longest prefix wins. Tie → cascade order (global
  allow, global deny, crew deny, crew allow), same as isaac-da0r.
- **Overlay, not replace.** Crew `:deny ["/proj/.env"]` adds a deny.
  Crew omitting `:directories` inherits global.
- Global deny of `~/.ssh` still applies if crew allows `~` (parent is
  weaker). Crew can re-open **that** prefix with `:allow ["~/.ssh"]`.
- **One tree for all `fs/*` tools** (read, write, edit, multi_edit, grep,
  glob). No split read vs write in this bean.
- **exec is not covered.** Directory ACL does not contain a shell.
  Do not assert that `:deny` of a path blocks `exec/run`.
- **Symlinks:** evaluate the resolved path.
- Clean cutover: whatever cwd/quarters heuristic exists today is replaced
  by this config. No back-compat aliases.

## Shape

```edn
;; isaac.edn
:tools {:allow :all
        :deny  [:exec/run]
        :directories {:allow [:cwd :quarters]}}

;; crew/scrapper.edn
:tools {:allow [:exec/run]
        :directories {:allow ["/Users/zane/agents/tonotop"]
                      :deny  ["/Users/zane/agents/tonotop/.env"]}}
```

## Acceptance (`@wip` in isaac-agent)

`features/tool/directories.feature` (file-level `@wip`):

- `bb features features/tool/directories.feature:18`
- `bb features features/tool/directories.feature:36`
- `bb features features/tool/directories.feature:66`
- `bb features features/tool/directories.feature:96`
- `bb features features/tool/directories.feature:127`
- `bb features features/tool/directories.feature:158`
- `bb features features/tool/directories.feature:184`
- `bb features features/tool/directories.feature:215`

New steps invented:

1. `a symlink {path} pointing at {target}`
   Existing file steps write regular files; this scenario needs a link
   whose resolved path is outside the allow root.

## Out of scope

- Sandboxing `exec/run`.
- Separate read vs write trees.
- Path globs other than “directory prefix” (`**`, file patterns).
- MCP.

## Held (awaiting human, 2026-08-25)

Escalated to human by **scrapper**@isaac-work-1. Blocking: isaac-ukg4 directory ACL (longest-prefix + da0r cascade) unit-tested green, but 5/8 approved `directories.feature` scenarios still fail under gherclj (cwd/quarters deny not applying on the live turn path; symlink Given cannot create `/work/project/link.txt` on host). Resumes only on explicit human action (re-hail the work/plan band, or re-promote). No crew re-picks this until then.



## Planner investigation (2026-09-06) — the ACL works; the scenarios were mis-authored
Reproduced the hold on the WIP branch `bean/isaac-ukg4` (c41e325, 67 behind main): `bb features features/tool/directories.feature` → 8 examples, 5 failures — four `Row 2: message.isError: Expected true, got: nil` (scenarios at :35, :65, :126, :183) and the symlink scenario (:214) dying in `java.nio.file.Files/createSymbolicLink` (GraalVM MissingReflectionRegistrationError under bb).

Traced `fs-bounds/ensure-path-allowed` on the live turn path: it IS called, the session IS found, the policy IS loaded (`global={:allow [:cwd]} ctx={:cwd "/work/project" :quarters "/isaac-state/crew/main"}`), and `names/path-allowed?` returns true for the cwd read and false for `/outside/secret.txt` (verified by re-running :35 with both tool calls queued before the text reply → row 1 toolResult isError=true, content `path outside allowed directories`). The four failing scenarios queue `tool_call, text, tool_call, text`: the drive ends the turn on the first text response, so the second (denied) read never executes, and the transcript matcher's third row lands on the only toolResult. The 'deny not applying on the live turn path' diagnosis in the hold note was wrong; the enforcement point is correct.

Symlink scenario: feature fixtures run on the memory fs (`session_steps/mem-fs`); `isaac.fs` has no symlink or canonicalization operations, and fs-bounds canonicalizes through the host (`io/file .getCanonicalPath`). A symlink cannot exist on the mem fs, so this scenario is unimplementable as written regardless of the reflection error.



## Decisions (2026-09-06, Micah)
1. The four deny scenarios (and the crew-extra allow scenario, which passed vacuously for the same reason) queue BOTH reads before the text reply. Why: the drive ends a turn on a text response; a denied read after a text never executes. Rewritten on isaac-agent main 1ee29cd — no new steps; the denied row asserts `isError true` and content `#"(?s).*path outside allowed directories.*"`.
2. Symlink coverage moves to the unit spec: `spec/isaac/tool/fs_bounds_spec.clj` proves `path-allowed?`/`ensure-path-allowed` evaluate the RESOLVED path using a real temp directory and `babashka.fs/create-sym-link` (bb-native; no java.nio reflection). Why: feature fixtures run on the memory fs, which cannot hold a symlink, and a real-fs fixture mode for one scenario is not worth the machinery. The feature scenario and the `a symlink … pointing at …` step are deleted (the step must be removed from session_steps on the branch).
3. Hold lifted; the WIP branch `bean/isaac-ukg4` (c41e325) is the starting point — rebase onto main (67 behind) first.

## Acceptance (replaces the list above)
- `bb features features/tool/directories.feature:18 35 63 91 120 149 174`
- 0 failures with the file-level `@wip` removed (7 scenarios).
- `bb spec spec/isaac/tool/fs_bounds_spec.clj` includes the symlink-resolution case on a real temp dir.
- `bb features && bb spec` green in isaac-agent; `features/tool/filesystem_boundaries.feature` cutover from the branch retained.
- Config validation (`checks.clj`) accepts `:tools :directories {:allow … :deny …}` at root and crew (branch already does; keep its specs).

## Handoff

branch: bean/isaac-ukg4 @ 68dad70485560d3caef29d0a15a94d3c771132e3 (base origin/main@dae7090ba3b9ca3db9cf4df51c397f68ac59aa33)

scrapper@isaac-work-1: directory ACL is yes/no per (crew, path). Empty = deny-all. Longest prefix wins; same-length uses da0r cascade (global allow, global deny, crew deny, crew allow). Overlay, not replace. fs/* only. Symlinks evaluated on the resolved path (unit spec, real temp dir). Config dir stays closed even when :quarters is granted. Traversal collapses `..` before matching. Default grover fixture grants `:cwd`/`:quarters` so existing suite reads still work. @wip removed from directories.feature (7/0).

Acceptance: bb spec 1676/0/3476; clojure -M:features 774/0/2031 (unwrapped; bb features 180s wrapper times out on this machine). focused: directories.feature + filesystem_boundaries.feature 18/0.



## Landed on main (2026-09-06)

main-sha: isaac-agent 68dad70485560d3caef29d0a15a94d3c771132e3



## Deployed (2026-09-08 15:30Z) — agent 0.1.50 (c54cbb0) + hail 0.1.15 (4d0f682)
Root grant applied first: `isaac.edn :tools {:directories {:allow [:cwd :quarters]}}`. After restart every CLI prompt failed with `invalid configuration … comms[:discord].crew references undefined crew … valid-values ["pilot"]`: 0.1.50's crew schema rejects the OLD vector form `:directories [:cwd …]` (the planner's earlier note that vectors were still accepted was wrong — only checks.clj tolerated them; the schema does not), so 11 crew files were dropped from the loaded config. Fixed at 15:36Z by rewriting each to `:directories {:allow […]}` (backups `*.bak-20260908-dirs`, committed in zane-isaac); `config validate` OK; pong OK. Six-minute CLI outage; hail attempts checked separately.
