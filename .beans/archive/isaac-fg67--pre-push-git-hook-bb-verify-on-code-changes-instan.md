---
# isaac-fg67
title: "Pre-push git hook: bb verify on code changes, instant on doc-only"
status: completed
type: task
priority: normal
created_at: 2026-05-09T14:19:37Z
updated_at: 2026-05-09T16:28:17Z
---

## Description

Workers occasionally push commits that break bb spec or bb features. Today the verifier role catches these reactively; a pre-push hook catches them at the source — locally, before any other worker pulls broken main.

The hook must short-circuit on doc-only changes. If the diff doesn't touch .clj/.cljs/.cljc/.feature/.edn, the hook exits 0 immediately. Code/test changes trigger bb verify (which runs bb spec + bb features --tags=~@wip).

## Tasks

### 1. Add bb verify task

`bb.edn` gets a new task:
```
:verify {:doc "Run all spec + non-wip features"
         :task (do (shell "bb" "spec")
                   (shell "bb" "features" "--tags=~@wip"))}
```

### 2. Create .githooks/pre-push

```bash
#!/usr/bin/env bash
set -e
remote_sha=$(git rev-parse @{upstream} 2>/dev/null || echo "")
local_sha=$(git rev-parse HEAD)
if [ -z "$remote_sha" ]; then
  changed=$(git diff --cached --name-only)
else
  changed=$(git diff --name-only "$remote_sha".."$local_sha" 2>/dev/null || echo "")
fi
echo "$changed" | grep -qE '\.(clj|cljs|cljc|feature|edn)$' || exit 0
bb verify
```

Executable bit set; tracked in repo so all clones get the same hook content.

### 3. Add bb hooks:install task

```
:hooks:install {:doc "Configure git to use repo-tracked hooks"
                :task (shell "git" "config" "core.hooksPath" ".githooks")}
```

Workers run this once per fresh checkout.

### 4. Update setup docs

AGENTS.md and any "first time setup" notes mention `bb hooks:install`.

### 5. Manual verification

- Commit only README.md change → push is instant (hook exits early)
- Commit a deliberately-failing .clj change → push is rejected by hook
- Commit a real .clj change with passing tests → push proceeds after bb verify completes

## Out of scope

- Pre-commit hook (different lifecycle; can come later if pre-push slowness is a problem in practice).
- Per-namespace test selection (defer until tests are slow enough to matter).
- Server-side enforcement on GitHub (the CI bead covers that).

## Why no Gherkin scenarios

Tooling/sysadmin work outside the Clojure surface. Acceptance is concrete files + manual verification.

## Acceptance Criteria

.githooks/pre-push exists and is executable; bb.edn has :verify task running bb spec + bb features --tags=~@wip; bb.edn has :hooks:install task setting core.hooksPath; pushing a doc-only commit completes instantly (hook short-circuits); pushing a deliberately-broken .clj commit is rejected by the hook; AGENTS.md or equivalent setup doc references bb hooks:install for new clones.

## Notes

Manual verification completed in temporary clones under /var/.../opencode: doc-only commit exited the pre-push hook immediately with no output; deliberately broken .clj commit was rejected by bb verify; passing .clj comment-only change proceeded after bb verify passed. I did not run bb hooks:install in the workspace because agent policy forbids mutating git config.

