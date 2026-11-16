---
# isaac-59om
title: isaac remote --help in the real CLI still prints foundation's generic usage
status: completed
type: bug
priority: normal
created_at: 2026-11-16T22:54:17Z
updated_at: 2026-11-16T23:02:37Z
---

isaac-hlak landed in the wrong place. Its help text lives in the remote command's run-fn. The packaged CLI answers `--help` in foundation's registry before the run-fn runs (`isaac.foundation.cli.registry`, the `#{"--help" "-h"}` checks), so the user sees only the usage line and options. hlak's scenario (remote.feature:453) calls the run-fn directly, so it passed.

Seen on the Mac (cli-proxy 1024ee6) and on zanebot (cli-proxy 0.2.0, foundation 0.1.31): `isaac remote --help` and `isaac help remote` show no subcommands and no configured remotes.

Fix: implement `isaac.foundation.cli.api/help` for `:remote` in cli-proxy and build the same text there; the registry already prefers `api/help` (registry.clj:75). The run-fn's help branch can call the same function.

Acceptance: features/integration.feature scenario 'isaac remote --help through the real CLI shows the remote's own help' passes.

feature-baseline: isaac-cli-proxy 36898acf7e46767349cbe878748cc97e339d7b31
feature-blob: isaac-cli-proxy features/integration.feature 49451ad7b0aa83a526849e299c40fb3c4744e9e7 86

## Implementation

Added `cli-api/help :remote` with shared remote-help rendering for the registry and the run-fn. The real CLI scenario revealed the test home-config setup did not bind the CLI user's home; the fixture now binds it so configured remotes are read from the actual pointer file. Removed only `@wip` from the baselined feature. `bb features-slow features/integration.feature:85`, `bb ci` (including after rebase), and `bb bean-gate verify isaac-59om --dir isaac-cli-proxy=../isaac-cli-proxy-59om-land` pass.

## Landed on main (2026-11-16)

main-sha: isaac-cli-proxy fa9848ed6b47f74bb2f1a42fda4429bf55fac0f6
