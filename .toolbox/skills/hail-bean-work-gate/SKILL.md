---
name: hail-bean-work-gate
description: Bootstrap and run gated bean work from a hail delivery — the worker implements, runs `bb bean-gate verify`, and lands its own bean on main when the gate is green. Use for isaac-work hails; falls back to the unverified/verify handoff for beans with no feature-baseline.
---

# Hail-driven bean work (gated)

Use when a hail (or band prompt) assigns bean work in a repo that carries the
**bean gate** (`bb bean-gate`, isaac). Everything up to "the tests are green" is
the same craft as before; what changes is the close:

> If `bb bean-gate verify <bean-id>` exits **0**, you land the bean on `main`
> and mark it `completed` yourself. No `unverified` tag, no hail to the verify
> band. Isaac CI re-runs the same gate on every completed baselined bean that
> reaches main (`bb bean-gate ci-scan`), so the contract is still checked by
> something other than you.

The gate freezes the **contract** (the planner's baselined scenarios, the
acceptance selectors, `## Exceptions`). It does not judge your design — that is
what the project skills and your own tests are for.

## Bootstrap checklist

Run in order before claiming or editing anything.

1. **Find the beans repo** — directory with `.beans/` and `.beans.yml` (always
   the `isaac` git clone). From session cwd, check `./isaac`, `../isaac`, or
   sibling role homes (`~/agents/isaac/work-1/isaac`, etc.). If hail prose names
   a path that does not exist, ignore the label and use the discovered clone.
2. **`git -C <isaac-clone> pull --rebase`** — beans and source sync together.
3. **`beans show <id>`** — read the full body + acceptance, including the
   `feature-baseline:` / `feature-blob:` lines and any `## Exceptions`.
   There is no `beans list --all`; use `beans list` or `beans show`.
   **If the bean is not found:** the dispatch may have raced the push — wait
   briefly, `git pull --rebase`, retry once. Still missing → do **not** pick
   another bean: reply on the incoming hail thread explaining, send a
   `<bean-id> ⚠️ **<crew>**@<session> bean not found — no action taken`
   notification, and stop.
4. **Find the implementation repo** — bean scope / title names the repo
   (`isaac-discord`, `isaac-hail`, monolith `isaac`, …). Work in the sibling
   checkout under your role home; `git clone` on demand per `AGENTS.md`.
5. **Skills** — try `list_skills` / `load_skill` if available. If empty or
   missing, read directly:
   - `../AGENTS.md` (shared boot)
   - `isaac/AGENTS.md` (`## Bean Workflow`, `## Planning`)
   - `isaac/.toolbox/commands/work-bean-gate.md`
   - this file
6. **Claim** — `beans update <id> --status=in-progress`, commit + push
   `.beans/` from the isaac clone.

All `beans` commands and bean markdown commits happen in the **isaac** clone
even when implementation edits happen in a module sibling. `bb bean-gate` only
runs from the isaac clone.

## Session cwd vs worktree

| Surface | Typical path | Holds |
|---------|--------------|--------|
| Role home | `~/agents/isaac/work-N/` | Session cwd, hail landing |
| Beans + toolbox | `~/agents/isaac/work-N/isaac/` | `.beans/`, `.toolbox/`, `bb bean-gate` |
| Module checkout | `~/agents/isaac/work-N/isaac-discord/` etc. | Split-repo source |

Hail init text ("checkout in quarters") describes intent, not a guaranteed path.
Authoritative rule: **the clone that contains `.beans/` is the beans repo.**

### Workspace protocol (shared checkouts are load-bearing)

- Work **in the sibling checkout** on a `bean/<bean-id>` branch. If you need
  isolation, `git worktree add ../<repo>-<bean-id> -b bean/<bean-id>` from the
  sibling — never a fresh full clone. Remove the worktree when you are done.
- **NEVER rename, move, symlink, or replace a sibling checkout.** Other
  sessions resolve `:local/root` deps against it.
- **Commit on green, always.** After every green run (`bb spec`, `bb features`,
  a focused scenario), commit to `bean/<id>` and push it. Never commit
  implementation work on a sibling's `main` before the landing step below.

## Commit trailers

Every commit from an orchestration session carries provenance:

```sh
git commit --trailer "Isaac-Session: <session-id>" --trailer "Isaac-Bean: <bean-id>"
```

`Isaac-Session` on every commit (beans repo and implementation repo);
`Isaac-Bean` on implementation commits while the bean is in flight. The gate
also uses these trailers: a `feature-baseline:` line first introduced by a
commit carrying an `Isaac-Session: isaac-work…` / `isaac-verify…` trailer is a
**gate failure** — baselining is the planner's job, never yours.

## Checkpoint

A checkpoint nudge is a save point inside the current turn, not a handoff or
turn end.

- If the latest test run is green, commit and push to the bean branch.
- Refresh the bean's done/next note in one edit: what is done, what is next,
  and the exact `file:line` or command where work resumes.
- If tests are red, record the failing command/result in that note and keep
  working; do not commit a claimed-green checkpoint.
- Continue the current turn after saving.

## Implement

Follow `isaac/.toolbox/commands/work-bean-gate.md` and the project skills
(`tdd`, `clojure`, `gherclj`, …):

- TDD + `bb spec` / `bb features` per the bean's acceptance.
- **Removing `@wip` is the only edit you make to any `.feature` file.** Not a
  reworded step, not a renamed scenario, not a deleted example row. The gate
  compares every baselined block (header, `Background`, each scenario) against
  the frozen blob with `@wip` stripped from both sides; anything else is a FAIL.
  If the scenarios are wrong, that is a conflict for the planner (below).
- Do not touch the bean's contract lines: `feature-baseline:`, `feature-blob:`,
  the `## Acceptance…` selectors, `## Exceptions`. They are append-only from
  the planner's side and untouchable from yours. Status, notes, checkpoints and
  `main-sha:` lines are yours to write.

### Cross-repo beans (`:dev-local`, not bean-branch sha pins)

While the bean is in flight, run the downstream suite against the sibling
checkout (`:dev-local` / `:local/root`), **not** a `:git/sha` pin at the
upstream `bean/<id>` branch — landing squashes and deletes that branch, so the
pin would dangle. The pin changes only at landing, and only to a sha that is
already on the upstream repo's `main` (see step 3 of Landing).

## Close: run the gate

When the acceptance is met and the suites are green, from the **isaac clone**:

```sh
bb bean-gate verify <bean-id>
```

Add `--dir <repo>=<path>` if a module checkout is not at `../<repo>`, and
`--ref <repo>=<ref>` to check a ref other than that checkout's `HEAD`
(`bb bean-gate --help` lists both). The exit code decides the close:

| Exit | Meaning | Your close |
|------|---------|------------|
| 0 | gate PASS | **Land it yourself** (below), then `completed` |
| 1 | gate FAIL — the contract moved | Revert the `.feature` to the baselined text, or hail the plan band |
| 2 | not gated (no `feature-baseline`) / usage error | Old path: `--tag=unverified` + hail the verify band |

### Exit 0 — land it

Per repo the bean's acceptance names, **upstream repo first**:

1. **Rebase and re-run the suite** in the sibling checkout:

   ```sh
   git fetch origin
   git checkout bean/<bean-id>
   git rebase origin/main
   bb ci                      # else bb spec && bb features
   ```

   A rebase conflict is a **stop-and-hail**, not something to resolve blind:
   `git rebase --abort` and hail the plan band with the conflicting files named
   (a conflict means someone else's landed work overlaps this bean's scope).
   Red suite → keep working; you are not done.

2. **Squash-merge to `main`** so main gets exactly one commit per bean per repo
   (commit-on-green leaves many checkpoints on the branch):

   ```sh
   git checkout main && git pull --ff-only origin main
   git merge --squash bean/<bean-id>
   git commit -m "<bean-id>: <bean title>" \
     --trailer "Isaac-Bean: <bean-id>" --trailer "Isaac-Session: <session-id>"
   git rev-parse HEAD                      # this sha is the repo's main-sha
   ```

   A conflict here is also a **stop-and-hail**: `git reset --hard origin/main`,
   leave the branch in place, hail the plan band. Do not resolve it.

3. **Downstream repos: repin BEFORE their own squash.** On each downstream
   `bean/<bean-id>` branch, rewrite any pin that still names the upstream
   pre-squash sha, then re-run that repo's suite:

   ```sh
   grep -l <pre-squash-sha> deps.edn bb.edn      # rewrite to the upstream main-sha
   git commit -am "<bean-id>: repin <upstream> to landed main sha" \
     --trailer "Isaac-Bean: <bean-id>" --trailer "Isaac-Session: <session-id>"
   bb ci
   ```

   Red → **do not squash**; fix it or hail. Green → squash-merge this repo per
   step 2. A pin may only name a sha reachable from that repo's `main`
   (`git merge-base --is-ancestor <sha> origin/main`) — never a bean-branch sha.

4. **Re-run the gate on the squash commit**, before pushing, in case `main`
   moved under the branch since the rebase:

   ```sh
   bb bean-gate verify <bean-id>              # from the isaac clone; still 0
   ```

   Non-zero → `git reset --hard origin/main` in the repo you just squashed and
   treat it as exit 1 / a conflict. Then push each repo's `main`.

5. **Record the landing in the bean** — append and commit (trailer):

   ```
   ## Landed on main (<YYYY-MM-DD>)

   main-sha: <repo> <sha>
   ```

   One `main-sha:` line per repo the bean touched. **A bean without a
   `main-sha:` line cannot be `completed`** — that line is what "on main" means
   to CI and to the next reader.

6. **Delete the bean branch** (locally and on the remote) once step 5 is
   committed — only then, so a bean that failed to land keeps its branch:

   ```sh
   git worktree remove ../<repo>-<bean-id>      # if you made one
   git branch -D bean/<bean-id>
   git push origin --delete bean/<bean-id>
   ```

7. **Complete the bean** from the isaac clone:

   ```sh
   beans update <bean-id> --status=completed
   git add .beans && git commit --trailer "Isaac-Session: <session-id>" \
     --trailer "Isaac-Bean: <bean-id>" -m "<bean-id>: landed on main, completed"
   git push
   ```

   No `unverified` tag. No hail to the verify band. Send the ✅ notification.

When the bean's implementation repo **is** `isaac` itself, steps 2 and 5–7 all
happen in that one clone: the squash carries the implementation, and the
`## Landed on main` note plus the `completed` status are a follow-up commit on
`main`. The gate reads the bean's recorded `main-sha:` commits when they exist,
so re-running `bb bean-gate verify <id>` after step 5 checks the landed tree.

### Exit 1 — the contract moved

The gate prints one `FAIL <reason>` line per problem. Two legitimate responses,
and only two:

- **You changed a baselined `.feature` beyond `@wip` removal** → revert that
  file to the baselined text (`git checkout <feature-baseline sha> -- <path>`
  in the module, then remove `@wip` again) and re-run the gate.
- **The scenarios themselves are wrong** (they contradict the code, each other,
  or the bean) → hail the **plan band** with the gate output quoted. The
  planner edits the feature on module `main` and re-baselines.

You **never** add a `## Exceptions` entry, never edit or delete a `feature-*`
line, and never run `bb bean-gate baseline`. All three are the planner's. A
worker-authored baseline is itself a gate failure (see Commit trailers).

### Exit 2 — not gated

The bean predates the gate (no `feature-baseline:`), so the old path is correct
and unchanged:

1. `beans update <bean-id> --tag=unverified` (stay `in-progress`), commit + push
   `.beans/`.
2. Hail the **verify band** — the value comes from the delivery's data block
   (`:verify-band`), never typed from memory; `reply_to` is the incoming hail's
   id. Band handoffs carry `band` and `params` **only** — no `session-tags`, no
   `crew` (extra filters can select zero recipients and park silently):

       {"band": "<verify-band value>", "params": {"bean-id": "<bean-id>"},
        "reply_to": "<incoming hail id>"}

   CLI equivalent when no `hail__send` tool is available:

       isaac hail send --band <verify-band> --reply-to <this-hail-id> \
         --params '{:bean-id "<bean-id>"}'

3. Send the ➡️ notification **after** the hail succeeds.

Exit 2 also covers usage errors (`bb bean-gate verify` with no id, an
unreadable `--dir`). Read the printed `bean-gate:` lines: if the bean *does*
carry a `feature-baseline:`, you have a broken invocation, not an ungated bean
— fix the command instead of taking the old path.

## Conflict → the plan band

For a gate exit 1 you cannot honestly revert, a rebase/merge conflict, or a
bean that contradicts the code or itself:

1. Append what you found to the bean body (quote the gate output) and push.
2. Hail the **plan band** (value from the data block) with a prompt override:

       {"band": "<plan-band value>", "params": {"bean-id": "<bean-id>"},
        "reply_to": "<incoming hail id>",
        "prompt": "Conflict on <bean-id>: <what the gate/merge reported and why it cannot be reverted>."}

3. Send the ➡️ planner notification after the hail succeeds. Leave the bean
   `in-progress`; the planner adjusts and hands it back via the work band.

## Process-test / no-op beans

When the bean body says **process test**, **no-op**, or **orchestration smoke**:

- **No product code or tests required** unless the bean explicitly asks.
- TDD rules are **suspended** for that bean.
- Such beans normally have no `feature-baseline:`, so the gate exits 2 and the
  old `unverified` + verify-hail path applies. If one *is* baselined, the gate
  and the landing steps apply unchanged.
- Minimum deliverable: claim, append `## Process Observations` to the body,
  file follow-up beans for gaps found, then close per the gate exit.

## Never end a turn in limbo

Every work turn ends in exactly one of: **completed** (gate green, landed,
`main-sha:` recorded, ✅ sent), **unverified handoff** (exit 2, verify hail
sent), **conflict hail sent** (plan band), or **HOLD + human escalate**.

**Do not hail yourself to continue.** No session-direct continuation hails, no
"continuation N of 5", no sending the next hail early. Finish in this turn; the
cycle budget comes from config, not from this skill. Running out of budget is a
**wrap-up**, not a hold: commit and push a `wip: <bean-id> checkpoint` on the
bean branch, write the done/next note on the bean, and stop — the delivery
worker resumes on a fresh turn.

HOLD only for a blocker you cannot remove: missing access or repo, a bean that
contradicts itself, a decision that is the planner's or a human's. Then send the
🆘 escalation (notification-comm + human-help-comm from the data block), append

```
## Held (awaiting human, <date>)

Escalated to human by **<crew>**@<session>. Blocking: <one-line synopsis>.
Resumes only on explicit human action. No crew re-picks this until then.
```

commit and push it, and stop. Escalation is terminal — do not re-hail.

## Notifications

**Notifications report completed actions, never intentions.** Send the hail or
run the command first, the feed line after it succeeds. Coordinates come from
the delivery's data block: comm = notification-comm's `:id`, target = its
`:channel`. Fill `<crew>` / `<session>` from your own identity block;
`<short-slug>` from the bean title.

- After claim: `<bean-id> 🟢 **<crew>**@<session> claimed (<short-slug>)`
- After landing + completing: `<bean-id> ✅ **<crew>**@<session> gate green, landed on main (<short-sha>) — completed`
- After a gate exit 1 you reverted and re-passed: `<bean-id> 🩹 **<crew>**@<session> gate FAIL reverted to baseline, re-passed`
- After the verify handoff hail (exit 2): `<bean-id> ➡️ **<crew>**@<session> handed off to verify (not gated)`
- After the planner handoff hail: `<bean-id> ➡️ **<crew>**@<session> handed off to planner (<gate conflict | merge conflict>)`
- On a resume (verify fail or planner return): `<bean-id> 🔁 **<crew>**@<session> resumed (<short-slug>)`
- On a CI-failure hail (band `isaac-ci-failure`): `<bean-id> 🔴 **<crew>**@<session> CI red on <branch> (<failing job>)` — not a resume; never 🔁 for it
- After a CI repair is pushed: `<bean-id> 🩹 **<crew>**@<session> CI repair pushed (<short-sha>)`

ID first for recognition; emoji for scanning (🟢 claim, ✅ landed, ➡️ handoff,
🩹 repair, 🔴 CI red, 🆘 escalation).

## Do not probe the hail CLI

Every `isaac hail send` that parses is a real send. Use `--help` to read flags
and `--dry-run` to check syntax. Never run it with placeholder values like `x`
or `t`.
