---
name: foreman-bean-work
description: "Work a bean dispatched by the Foreman isaac-bean-work machine. Same craft and gate as hail-bean-work-gate; the difference is how the work arrives and how you report: no hails, no notifications — the last line of your reply is the outcome."
---

# Foreman-driven bean work

Load **hail-bean-work-gate** too. Everything in it about the craft applies
unchanged: Bootstrap checklist, Workspace protocol, Commit trailers,
Checkpoint, Implement, Cross-repo beans, Close: run the gate, and every step of
"Exit 0 — land it". This skill replaces only the parts about hails and
notifications.

## How the work arrives

- Your turn's preamble says it is part of Foreman machine `isaac-bean-work`,
  instance `<bean-id>`, and lists the events you may end with. **The instance
  id is the bean id.**
- The prompt's first line is `Work bean <bean-id>: <title>`. The bean is the
  source of truth: `git pull --rebase` the isaac clone, then `beans show <bean-id>`.
- Your working directory is a leased worksite (one of the `work-N` role homes).
  It may not be the one you used last time for this bean. If the bean already
  has a `bean/<bean-id>` branch, fetch it and continue from it; it holds your
  last checkpoint.
- There is no hail and no data block. Ignore every instruction in
  hail-bean-work-gate that sends a hail, uses `reply_to`, reads a band or comm
  from a data block, or sends a notification.

## How you report: the last line of your reply

End your final reply with exactly one line, and nothing after it:

    event: landed
    event: conflict <one-line reason>

Foreman reads that line and moves the machine. You do not need any Foreman tool.

| Situation | Do this, then end with |
|-----------|------------------------|
| Gate exit 0, landed per "Exit 0 — land it", `main-sha:` recorded, bean `completed` | `event: landed` |
| Gate exit 1 you cannot honestly revert (the scenarios are wrong) | append the gate output to the bean, push, `event: conflict gate: <first FAIL line>` |
| Rebase or squash conflict during landing | abort/reset as the gate skill says, note the files on the bean, push, `event: conflict merge: <files>` |
| Gate exit 2 (the bean is not gated) | do not land; note it on the bean, `event: conflict not gated` |
| A blocker only a human or the planner can remove (missing access, a bean that contradicts itself) | write the `## Held` note, push, `event: conflict held: <one-line synopsis>` |

A `conflict` stalls the bean and alerts the planner with your reason, so make
the reason specific enough to act on.

## Running out of cycle budget

That is not a conflict. Commit and push a `wip: <bean-id> checkpoint` on the
bean branch, write the done/next note on the bean, and end your reply **without
an event line**. Foreman treats the quiet turn as stalled and the planner
resumes it with `dispatch`; you pick up from the branch and the note.

## Never

- Never send a hail, and never call `isaac hail send`.
- Never write an `event:` line you did not earn (landed means on main and
  `completed`).
- Never put anything after the event line.
