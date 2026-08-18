---
# isaac-q3uk
title: sessions list SIZE must stat the transcript file, not parse it
status: completed
type: bug
priority: high
created_at: 2026-08-18T13:48:47Z
updated_at: 2026-08-18T17:56:13Z
---

## Goal

`isaac sessions list` hangs on zanebot (~5 min) because SIZE slurp-parses and re-serializes every transcript. We want **on-disk file size** (`ls -l`), not a reconstructed JSON byte count.

Observed 2026-08-18: 1.3G under `~/.isaac/sessions`, `isaac-work-1.jsonl` 41MB, `tono-work-1.jsonl` 36MB. List eventually prints; it is not deadlocked.

## Settled design (2026-08-18, Micah)

- SIZE = `fs/size` of `sessions/<session-file>`. Missing file → 0.
- Never call `get-transcript` / `migrate-transcript!` / `transcript-byte-offset` on the list path.
- Leave `transcript-byte-offset` alone — it is the compaction offset helper, not a table metric.
- Add `fs/size` on the existing `Fs` protocol (only RealFs + MemFs, same file):
  - RealFs: `File.length` (O(1), no content I/O)
  - MemFs: UTF-8 byte length of the stored string (features stay hermetic)
- Do **not** slurp-and-count on RealFs as a shortcut.

## Home

- `isaac-foundation` — `fs/size`
- `isaac-agent` — `isaac.session.cli/transcript-size-bytes`; bump foundation pin

## Acceptance

Existing scenario (expected output unchanged):

```
cd isaac-agent && bb features features/session/cli.feature:237
```

Also:

```
cd isaac-agent && bb spec spec/isaac/session/cli_spec.clj
cd isaac-foundation && bb spec spec/isaac/fs_spec.clj
```

DoD:

- [ ] `fs/size` on RealFs + MemFs; missing path → nil or 0 (pick one, use it)
- [ ] list SIZE uses `:session-file` + `transcript-path` + `fs/size`
- [ ] spec: listing does **not** invoke `get-transcript` (redef/spy)
- [ ] existing SIZE scenario still green (`compact-chat` `\d+B`, `roomy-chat` `\d+(\.\d)?K`)
- [ ] `transcript-byte-offset` callers (compaction) unchanged

## Worker notes

- Current smell: `cli.clj` `transcript-size-bytes` → `store/get-transcript` → `transcript-byte-offset` (parse + re-serialize every line).
- `isaac sessions list --json` already skips SIZE; table path is the bug.
- Follow-up not in scope: `isaac session` (singular) is not a command.



## Verify fail (attempt 1, 2026-08-18): foundation pin bump regresses config specs

Evidence:
- Verified acceptance still passes on isaac-agent 7ab03ab: `bb spec spec/isaac/session/cli_spec.clj`, `bb features features/session/cli.feature:237`, and isaac-foundation `bb spec spec/isaac/fs_spec.clj`.
- Current work commit regresses config validation after the foundation bump to `4d25fb6`: `cd isaac-agent && bb spec spec/isaac/config/schema_spec.clj spec/isaac/config/provider_validation_spec.clj` -> 21 failures.
- Same targeted config command passes on the parent checkout before this bean (`f4328f37`, prior foundation pin `c70d9e2`): 22 examples, 0 failures.
- `cd isaac-agent && bb spec` also fails with the same 21 config failures on current HEAD.

Needs fix: keep the SIZE/fs/size change without regressing config schema/provider validation.

## Repair (attempt 1, 2026-08-18, scrapper@isaac-work-2)

Root cause: 7ab03ab pinned agent at foundation **main tip** `4d25fb6` (flgy loader split + fs/size). That surface regresses agent config schema/provider validation (`isaac.fs/instance` + marigold vs agent messages). Same class of break as isaac-rg61.

Fix: keep fs/size + list SIZE, but pin agent to an agent-safe SHA — same pattern as `isaac-rg61-agent-pin`.

- Foundation tag `isaac-q3uk-agent-pin` = **0b9ecdf** = `c70d9e2` + cherry-pick of fs/size only (not on main; main still has `4d25fb6`).
- Agent **59edb36** pins deps.edn + bb.edn to `0b9ecdf`.

Verified:
- `bb spec spec/isaac/config/schema_spec.clj spec/isaac/config/provider_validation_spec.clj` → 22/0
- `bb spec spec/isaac/session/cli_spec.clj` → 21/0
- `bb features features/session/cli.feature:237` → 1/0
- `cd isaac-foundation && bb spec spec/isaac/fs_spec.clj` → 52/0 (main `4d25fb6` still has fs/size)
- `bb spec` (agent) → 1312 examples, 0 failures, 3 pending (claude-cli @real smokes)

Do not pin agent to flgy tip until a dedicated cutover.


## Repair (attempt 2, 2026-08-18, scrapper@isaac-work-1)

CI 32157141573 on 445f593 still failed features 655/1 on built_in.feature:195
(Constructor.newInstance). Memory persist was already pr-str; the remaining
path is sidecar write-transcript! → write-json → cheshire generate-string.

built_in.feature uses clean-test-dir (no memory-store hook), so
"the following sessions exist" creates via sidecar/impl-common.

Fix: encode transcript JSON locally in impl-common/write-json (no
Constructor.newInstance). Specs redef generate-string to prove the
native-bb-sensitive path is gone.

Verified:
- bb spec spec/isaac/session/store/impl_common_spec.clj → 9/0
- bb spec spec/isaac/session/cli_spec.clj + store specs → 120/0
- bb spec → 1315 examples, 0 failures, 3 pending
- bb features features/session/cli.feature:237 features/tool/built_in.feature features/session/storage.feature → 41/0

Agent commit **145c888**.
