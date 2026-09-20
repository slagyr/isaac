---
# isaac-nceb
title: 'Provider auth failure must classify as weather: expired claude OAuth returns empty content, so hails dead-letter'
status: todo
type: bug
priority: high
created_at: 2026-09-20T07:34:11Z
updated_at: 2026-09-20T07:34:11Z
---

2026-09-20 ~05:20Z zanebot's `claude` CLI OAuth session expired. What Isaac saw
was not an auth error: every turn ended `:empty-terminal-response` ("model
returned no content after continuation retry"). Hails burned all five attempts
and dead-lettered (isaac-8s6s's siblings among them), one verify session
replied a single sentence with no tool calls, and nothing in the log named
auth. The actual message was sitting one layer down, in the CLI's own stderr:

    Failed to authenticate: OAuth session expired and could not be refreshed

Work:

- isaac-claude-code: recognise the CLI's authentication failure (stderr, exit
  code, or the absence of any stream at all) and return an auth-class error
  rather than an empty response.
- isaac-agent / hail: an auth-class error is weather — defer the hail with
  attention, per [[hails-never-die]], instead of counting attempts toward the
  dead-letter budget.
- Scenario: a claude-cli invocation that fails to authenticate ends the turn
  with an auth error, and a hail delivered into it defers rather than
  dead-letters.

Related: isaac-zz6d (verify turns ending with zero tool calls — same outage,
possibly the same root), isaac-v64q (429/401 mid-stream on the Responses path).
The five dead letters from this outage are `d4a7cd6f` (isaac-ddls work, which
had already handed off), `f479c534` (isaac-dymn work) and three isaac-przv
verify hails.
