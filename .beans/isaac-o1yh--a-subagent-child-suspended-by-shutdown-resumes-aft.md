---
# isaac-o1yh
title: A subagent child suspended by shutdown resumes after the restart
status: draft
type: bug
priority: high
created_at: 2026-11-17T14:38:08Z
updated_at: 2026-11-17T14:38:08Z
---

Draft. Follows isaac-hkfw, which only stops the damage.

When the server shuts down cleanly with a subagent child in flight, the child
should carry on after the restart and its parent should get the result. On
zanebot (2026-11-17, 05:09:29Z and 05:32:33Z) 23 children were suspended and
none had a turn marker at the next boot (`:resume/scan-complete :markers 1`,
the 1 being an unrelated weather-parked session), so none resumed. A comm
turn suspended the same way does get its marker stamped
(`features/bridge/suspend.feature`).

To find first: why the marker is missing for a turn the queue worker runs.
Candidates: the worker path clears the marker before
`suspend/release-turn-marker!` can stamp it; or the turn had already ended in
error (`:mcp-unavailable`, because the SIGTERM went to the whole process
group and killed the Claude CLI under it) when the suspend arrived.

Related lab work on zanebot, in `/Users/zane/agents/zane-lab/experiments/`:
`graceful-observer` (agent.v2 + foreman.v2 patches: a resumed turn keeps its
observers), `resumed-observer-fix`, `stall-postmortem`.

Needs before it is ready: a reproduction in a feature (a child with a
waiting model response, in-flight turns suspended, boot resume, the child
finishes and the parent hears it), and a check that the queue-tick step can
hold a child in flight.
