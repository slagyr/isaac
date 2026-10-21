---
name: foreman-bean-verify
description: "Verify an ungated bean dispatched by the Foreman isaac-bean-work machine. Review acceptance, land on main before completion, and report the verdict as the reply's last event line; no hails or notifications."
---

# Foreman-driven bean verification

Use the verification craft in **hail-bean-verify** and the project's `/verify` command, but **not** their hail or notification protocol. The Foreman preamble identifies machine `isaac-bean-work`, instance `<bean-id>`, and the events permitted by this turn. The bean is the source of truth. Do not select another bean from the unverified queue.

1. Find the isaac clone (`.beans/`), `git pull --rebase` there before reading, and `beans show <bean-id>`. Find each implementation repo named by acceptance and pull it. A worker handoff is `in-progress` + `unverified`. Do not review a dirty tree or silently treat missing work as success.
2. Verify acceptance with fresh eyes: inspect the implementation commits and any feature changes (`@wip` removal only unless the bean's `## Exceptions` authorizes more); run the specified tests, check output and test quality, and confirm downstream pins name shas reachable from upstream `origin/main`. A bean with unrun/red acceptance, missing implementation, or lingering `@wip` fails.
3. On pass, **land every implementation repo on main before completing**: fetch, rebase `bean/<id>` on `origin/main`, rerun acceptance, squash-merge into updated main, repin downstream branches to upstream *landed* shas before their own squash, and push main. A merge conflict or red tests are a failure; abort/reset instead of resolving blind. Append `## Landed on main (<date>)` with a `main-sha: <repo> <sha>` line for each touched repo. If the worker already landed on main, verify the recorded sha is reachable from `origin/main`. Never mark a bean completed without its main-sha. Delete landed bean branches only after the landing note is committed.
4. Pass: `beans update <id> --status=completed --remove-tag=unverified`; commit/push `.beans/` with `Isaac-Session` and `Isaac-Bean` trailers. End the reply with `event: verified`.
5. Fail: append `## Verification failed` with HEAD, working-tree summary and the reproducible reason; `beans update <id> --status=in-progress --remove-tag=unverified`; commit/push the note. End the reply with `event: repair`. Foreman sends it back to work. On a blocker only a person can clear, write a held/blocking note, commit/push and end with `event: conflict <one-line reason>` instead.

Every orchestration commit has `Isaac-Session: <your session>`; implementation commits also have `Isaac-Bean: <id>`. Do not call `isaac hail send`, send a notification, or signal Foreman yourself. Foreman consumes **only** the last line of your reply. End with exactly one of:

    event: verified
    event: repair
    event: conflict <one-line reason>

Do not emit an event until the corresponding bean updates and pushes have succeeded. If the turn runs out of budget, save a done/next note on the bean, commit/push green work, and reply without an event so the verify side stalls for human dispatch.
