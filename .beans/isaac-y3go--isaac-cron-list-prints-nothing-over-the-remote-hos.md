---
# isaac-y3go
title: 'isaac cron list: prints nothing over the remote (hosted commands get no :config), and its columns run together'
status: in-progress
type: bug
priority: normal
created_at: 2026-11-16T23:32:57Z
updated_at: 2026-11-16T23:33:53Z
---

Two problems with `isaac cron list`, both seen on zanebot 2026-11-16 after the cron 0.1.8 cutover. Micah: "yes please" (bean both together).

1. **Over the remote it prints nothing.** `isaac remote zane -- cron list` prints no rows and exits 0. On zanebot itself it lists all six jobs. Cause: foundation's embedded dispatch (`isaac-foundation/src/isaac/foundation/cli/host.clj`, `dispatch-embedded`) calls a hosted command's run-fn with only `:root`, `:display-root` and `:_raw-args`. A local run (`main.clj:134`) also passes `:config` (and `:load-result`). `cron list` reads `(:config opts)`, gets nil, and lists nothing. Any command that reads `:config` breaks the same way when hosted.
   Fix in foundation: embedded dispatch passes the server's live config (the nexus `:config`) as `:config`, in the same shape a local run gets.
2. **Columns run together.** `list-jobs` pads names to 10 (`%-10s`) with no separator, so `health-checkin` and `tempest-vault-sync` touch their rules. New layout: a NAME / WHEN / NEXT header; each column padded to its longest value plus two spaces; the last column unpadded. An empty list prints "No cron jobs." The old scenario that fixed the bad widths in place is replaced.

Repos: isaac-foundation (dispatch fix), isaac-cli-server (fixture `fx-config` and step "the server's live config is:" in `spec/isaac/cli_server/cli_server_steps.clj`; its pin moves to the foundation fix), isaac-cron (layout).

Acceptance: the @wip scenarios named in feature-baseline pass; the rest of each repo's suite stays green.

feature-baseline: isaac-cron 490ca7dd36b9ce420d0729c7938180c8ee125fca
feature-baseline: isaac-cli-server a15a760bc06dfe2166577cd606c175a609ade194
feature-blob: isaac-cron features/rules.feature a4d852bf03969dbc6322407fa6a4dcafb3dba517 147,164
feature-blob: isaac-cli-server features/cli/endpoint.feature a58a2e26afe5a753d4b94672f478524adf6a5aae 55
