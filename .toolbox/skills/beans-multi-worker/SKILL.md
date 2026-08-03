---
name: beans-multi-worker
description: Use this skill when multiple workers (parallel checkouts, separate machines, or human + agent) share a single beans tracker. Covers session close protocol, when to pull, and resolving git conflicts on bean files.
---

# Beans Multi-Worker Sync

## When This Skill Applies

Use this skill when multiple worker checkouts, separate machines, or a mix of human and agent collaborators share one beans tracker. Beans live as plain markdown in `.beans/` under version control, so multi-worker discipline reduces to **normal git discipline** — but the failure modes and conflict shapes are worth calling out.

## The Rule

**Before acting on another worker's output, `git pull`. After producing your own, `git push`.**

Pushes are cheap. Pulls are situational. The asymmetry matters — push proactively, pull at moments where you're about to act on what someone else produced.

Unlike a SQL-graph tracker that needs a separate sync step, beans state moves with the code. One `git pull` brings both. One `git push` sends both. No second protocol to remember.

## Push after every bean write

Bean state changes touch real files. Push them so other workers can see what's happening.

- After `beans create`, `beans update`, `beans delete`, `beans archive` → `git add .beans/ && git commit -m "..." && git push`.
- The bean change usually piggybacks on the related code commit. Claiming a bean (`status=in-progress`) is fine as a standalone commit. Completing a bean ships with the code change.

## Pull at handoff points

Pulls are situational — only at moments where you're about to act on what someone else produced. Do **not** pull on every `beans show`/`beans list` — that's wasteful.

### Verification handoff

Running `/verify` or otherwise reviewing a bean tagged `unverified`:

```bash
git pull --rebase
```

…**before** reading source or bean state. One pull covers both.

Verification is the canonical worker→reviewer handoff. Skipping the pull risks reviewing stale code or overwriting concurrent reviewer notes.

### Resuming after external change

If you've been told "the bean was reopened," "the verifier left notes," "the user closed a dependency," or any signal that another actor touched your bean since you last looked:

```bash
git pull --rebase
```

…before `beans show <id>`. Your in-memory model of the bean is stale.

### Session start

```bash
git pull --rebase
```

You don't know what landed while you were away.

## Sequence pull → read → write — never parallelize

`git pull --rebase`, `beans show`, `beans list`, and bean writes (`beans update`, `beans create`) are **state-dependent**. They MUST run sequentially.

The trap: agent harnesses that support parallel tool calls make it tempting to batch `git pull --rebase` and `beans show <id>` in one parallel block. That is unsafe. The bean read can complete against the pre-pull state, giving you a stale view that contradicts the post-pull truth. Symptom: "first check says not ready, second check says ready, with no apparent change between them."

Rule:

- Run `git pull --rebase` alone. Wait for it to finish.
- THEN run `beans show` / `beans list` / any verification reads.
- THEN run bean writes, commits, pushes — one at a time.

Do NOT issue these in the same parallel batch, even when the harness allows it. The performance saving is small; the cost of acting on stale bean state is real.

## Session Close Protocol

When ending a work session, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

```
[ ] 1. git status              (check what changed)
[ ] 2. git add <files>         (stage code AND bean changes)
[ ] 3. git commit -m "..."     (one commit covers both)
[ ] 4. git pull --rebase       (rebase on remote)
[ ] 5. git push                (push to remote)
[ ] 6. git status              (must show "up to date with origin")
```

### Critical rules

- Work is NOT complete until `git push` succeeds.
- NEVER stop before pushing — that leaves work stranded locally.
- NEVER say "ready to push when you are" — YOU must push.
- If push fails, resolve and retry until it succeeds.
- File new beans for remaining work before pushing — capture follow-ups in beans, not in your head.

## Conflict resolution

When two workers update the same bean concurrently, `git pull --rebase` may report a merge conflict on `.beans/<id>--*.md`. Resolve like any other text-file conflict:

```bash
git pull --rebase
# CONFLICT: .beans/isaac-abc--something.md
# Open the file, resolve the <<<<<<< / >>>>>>> markers
git add .beans/isaac-abc--something.md
git rebase --continue
git push
```

Common shapes:

- **Status race** — two workers claim or close the same bean. Pick the right end state (usually the later one wins; if the work was actually done by one, their state should stick).
- **Tag drift** — one worker added a tag, another removed it. Decide which is correct based on intent (the verifier removing `unverified` should stick; a claim that bypassed it should not).
- **Body drift** — both workers appended verification notes. Concatenate both.

After resolving, commit the merge and push. Do not force-push.

## What's gone vs the beads era

- **No separate sync command.** `bd dolt push/pull` is gone. `git push/pull` does everything.
- **No silent stranding via failed sync.** If your `git push` succeeds, your bean state is visible. If it fails (non-fast-forward), you know immediately and pull/resolve.
- **No `--theirs` / `--ours` SQL conflict resolution.** Conflicts are text-file conflicts.
- **No Dolt credentials, no remote service to be down, no out-of-band push step.** One credential surface: git.

## When this skill does NOT apply

Single-worker projects with one machine and one human. The discipline is the same shape as ordinary git hygiene; no extra protocol needed.
