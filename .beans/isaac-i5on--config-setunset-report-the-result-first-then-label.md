---
# isaac-i5on
title: config set/unset report the result first, then labelled validation warnings scoped to the changed path; crews can acknowledge broad directory grants
status: todo
type: feature
priority: normal
tags:
    - foundation
    - config
created_at: 2026-09-25T14:46:38Z
updated_at: 2026-09-25T14:50:42Z
blocked_by:
    - isaac-gs4a
---

Repos: **isaac-foundation** (src/isaac/config/cli/mutate_common.clj `handle-mutate-result!`, src/isaac/config/cli/common.clj `print-warnings!`) and **isaac-agent** (src/isaac/config/checks.clj `check-crew-broad-directories`, plus the crew `:tools :directories` schema). Scenarios live in isaac-agent.

## The problem (zanebot, 2026-09-25)

Every `config set` on zanebot prints 10 unrelated warnings to stderr **before** its own result. Two of them are unknown keys; eight are `crew.*.tools.directories` broad-grant warnings that Micah chose deliberately. The one line that matters (the result, or the error) is buried at the bottom. A `--force` write then says "wrote … with 11 validation error(s) outstanding" when there was one error; the count includes the warnings.

## Change

1. **Result first.** A mutation's first line is its outcome: the confirmation on stdout, or the error on stderr.
2. **Labelled warnings after it, on the same stream.** Under a `Validation warnings (N):` heading, one per line as `<path> - <message>`. On success they go to stdout after the confirmation; on refusal, to stderr after the error. The "N validation error(s) outstanding" line goes away.
3. **Only the path you changed.** List warnings for the changed path's entity (e.g. `models.echo.*` when setting `models.echo.model`, `crew.joe.*` when setting `crew.joe.model`). Collapse everything else into one line, `N other validation warning(s) — run: isaac config validate`. `config validate` still lists everything.
4. **Acknowledged broad directory grants.** A crew can set `:acknowledge-broad? true` beside `:allow` in `:tools :directories`. `check-crew-broad-directories` skips that crew entirely, in validate and everywhere else. An unacknowledged broad grant is still reported by `config validate`.

`--edn` / `--json` output is unchanged: human lines stay out of the structured record.

## Decisions

- Decision (2026-09-25, Micah): "We should start with the success message of the command or the error and then after that we can list all of the warnings but we should say what they are: they're validation warnings."
- Decision (2026-09-25, Micah): "I made a choice to grant those directories to those crews. I don't need to be warned about it all the time." He approved the planner's proposal: scope warnings to the changed path, plus an acknowledgement key. The alternative, making the directory check validate-only, was rejected because a new accidental broad grant would then pass silently everywhere except `validate`.
- Related, not in scope: isaac-6eu6 (unknown-key warnings on every non-config command). Item 3 here covers `config set`/`unset` output only.

## Scenarios (isaac-agent, @wip)

Existing scenarios rewritten (whole scenario `@wip`):
- features/config/set_unset.feature:253 — `--force` set: confirmation, then `Validation warnings (1):`, then `models.echo.provider - is required`; no "error(s) outstanding"
- features/config/set_unset.feature:281 — `--force` unset: same order

New file features/config/set_report.feature (file-level `@wip`):
- features/config/set_report.feature:19 — warnings elsewhere collapse to a count after the confirmation
- features/config/set_report.feature:37 — a refused set leads with the error, then the count
- features/config/set_report.feature:53 — `config validate` still reports an unacknowledged broad grant (passes today; it guards the regression)
- features/config/set_report.feature:67 — an acknowledged broad grant is never reported

Fixture note: set_report.feature binds the user home with `the user home directory is "/tmp/grover-home"` and deliberately omits `default Grover setup`, which clobbers that binding.

## Acceptance

```
cd isaac-foundation && bb spec && bb ci
cd isaac-agent && bb features features/config/set_report.feature features/config/set_unset.feature:253 features/config/set_unset.feature:281
cd isaac-agent && bb features features/config && bb ci
```

Remove `@wip` from set_report.feature and the two rewritten scenarios; all of features/config is green. Land foundation first, repin agent, then land agent.

## Sequencing (planner, 2026-09-25)

Blocked by isaac-gs4a: both edit isaac-foundation mutate_common.clj. Dispatch after gs4a lands, and rebase onto its foundation main.
