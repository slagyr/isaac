---
name: planning
description: Use this skill when co-authoring beans and Gherkin scenarios with the user — planning sessions that turn intent into feature files and implementation beans. Covers the partnering craft: investigate before asserting, settle design before drafting, ask vs. decide, recording decisions, and the anti-patterns that wreck planning sessions.
---

# Planning Partner

How to be a good partner when co-authoring **beans + Gherkin scenarios** with the user.

This is the **craft layer**. It does not repeat the mechanics — for those, read first:
- The `plan` / `plan-with-features` commands — the feature-first workflow, `@wip`, bean lifecycle.
- The `gherclj` skill — step/helper structure, contract integrity.
- The project's table dialect reference (e.g. `features/TABLES.md`) if one exists.

Those tell you *what the workflow is*. This tells you *how to run it well* — and which mistakes quietly wreck a planning session. If you only remember one thing: **investigate before you assert, settle the design before you draft, and write nothing the user hasn't approved.**

## Project Extensions

This skill is generic. Each project defines its own specifics — look for them in the project's `AGENTS.md` or planning doc before your first session there:

- **Repo layout** — where beans live vs. where feature files live (same repo, or a planning repo coordinating several project repos).
- **Fixture theme** — the established fictional cast/naming for scenario content. If none exists yet, propose one and get it approved before drafting.
- **Compliance constraints** — domains with regulated data (health, finance) upgrade "no real data in specs" from hygiene to violation, and may name seams whose changes have compliance blast radius.
- **Back-compat stance** — the default here is clean cutover; a project may declare otherwise.

---

## 1. Mindset

**You are read-only on source. Your outputs are scenarios and beans.** Never edit production code while planning. If you touch a source file by accident, revert it.

**Ground every claim in the code. Do not assume behavior.** The single most common failure is describing what code "probably does" instead of reading it. A confident wrong claim costs more than the 30 seconds it takes to check, because the user builds decisions on it. When you haven't checked, say so.

**Design before scenarios. Scenarios before beans. Beans before code.** Scenarios *encode* design decisions into tables; if the design is still open, you'll draft tables you have to throw away. Resolve the open questions first (see §3), then the scenarios fall out cleanly.

**Propose in chat; commit only on approval.** During design discussion, put proposals in the conversation — do not write them to beans, feature files, or code until the user explicitly approves *that* decision. A bean can be *created* `draft` to capture intent, but design notes inside it should reflect decisions the user has actually signed off on.

**When you're wrong, correct immediately — with evidence.** Don't defend a wrong claim. State the correction, show the code that proves it, move on. A partner who self-corrects is trusted; one who rationalizes is not.

---

## 2. The loop

1. **Listen.** What's the actual behavior change? What's the seam? Don't start drafting on an ambiguous ask.
2. **Investigate (read-only).** Read the real code paths. Find the existing steps. Find the fixtures. Find the schema. (See §4.)
3. **Clarify the design.** Surface genuine forks as crisp either/or questions with a recommendation (see §5). Settle them *before* drafting tables.
4. **Propose scenarios, one at a time.** Themed and fictional, reusing existing steps, flagging new ones. Get a nod, then the next.
5. **Record decisions in the bean as you go** (see §6) — date-stamped, with rationale.
6. **On approval:** write the `@wip` feature file, commit + push, then create/promote the bean to `todo` with runnable acceptance commands.

You move down this list, not around it. Skipping step 2 produces wrong scenarios. Skipping step 3 produces scenarios you rewrite. Skipping the one-at-a-time review produces a wall the user rejects wholesale.

---

## 3. Settle the design before you draft

Scenarios are the *output* of a settled design, not the place to figure it out. Before drafting:

- **Identify the open questions.** List them.
- **Drive each to a decision** — a recommendation plus the trade-off, or a crisp question if it's genuinely the user's call.
- **Watch for decisions that reshape the data model.** These change every table. Catch them early; discovering them mid-scenario means rewriting everything above.
- **Re-derive the consequences.** When a decision lands, trace what *else* it removes or touches. Surface those so the user confirms the full blast radius — especially on seams the project has flagged as load-bearing.

Only when the model is settled do you start writing `| param | type | required |` rows.

---

## 4. Investigation discipline (where most mistakes hide)

**Reuse steps — but judge their *adequacy*, not just their existence.** `gherclj steps` / `gherclj match` tell you a step exists. They don't tell you it's *strong enough* for your assertion. A step that checks tool *names* cannot assert a tool *schema*. Recognizing "the existing step is too weak, I need a new precise one" is a core skill — and a new step is real work, so flag it explicitly and let the user veto it before it exists.

**Read the actual fixtures, don't invent.** Reuse the project's established fictional names. Inventing parallel fixtures is a smell.

**Confirm the abstraction level.** A scenario for a *generic* component must not bake in a *concrete* implementation's contract. Test the generic seam with fictional fixtures; push concrete assertions down to the module that owns them. Wrong-level scenarios are a top rejection reason.

**Every `Given` must be executable, not implied.** If a scenario needs configured state, the setup must *configure* it. No hand-waving "assume one exists." When the user asks "wait, no config is required to set that up?" — that's the smell that your setup is incomplete. Trace every precondition the code actually requires and make it a real `Given`.

**Find the right home / layer.** Shared code belongs at the lowest layer that *consumes* it or is the true common ancestor of its consumers — not reflexively at the base. Verify dependency direction before claiming a home. Respect the project's declared namespace/layer boundaries.

---

## 5. Ask vs. decide

- **Decide yourself:** anything with a conventional default or verifiable in the code (a filename, a flag, the obvious test path). Make the call, state it, move on. Don't make the user choose trivia.
- **Ask:** genuine forks where the answer changes what you build and you can't resolve it from code or convention. Make it a crisp **either/or with a recommendation** and the trade-off — not an open-ended musing, not a survey. One or two options, your lean first.
- **Default to the clean cutover.** Unless the project declares otherwise, assume **no legacy / no back-compat**. When a redesign removes or renames something, take the breaking-clean path — and make it *explicit* in the bean (removed keys hard-reject, no deprecated aliases, old scenarios deleted not retained).

---

## 6. Record decisions in the bean as you go

A bean is the durable contract. As decisions land, append them — don't keep them only in chat.

- **Date-stamp and attribute:** "Decision (2026-06-23, <user>): …". A worker months later needs to know it was deliberate.
- **Capture the *why*, not just the *what*.** "Namespaced keys because the union schema would collide otherwise" survives; "use namespaced keys" doesn't.
- **Record the per-scenario verdict** when reviewing an existing feature: keep / rewrite / remove / new, with the one-line reason. That review *is* the implementation contract.
- **Note the ripple/scope** a decision creates (other files, other repos, migration). Workers need the full surface.
- **Park deferred questions explicitly** ("DEFERRED — not decided: …") so they're preserved without reading as settled.

Append, commit, push — every time. Don't batch a session's decisions into one end-of-day write; another agent may be reading the bean in between.

---

## 7. Scenario drafting technique

- **One or two at a time.** A whole feature file is a wall; a rejected wall wastes more than twelve single reviews.
- **Number them** as you review so you and the user can refer back.
- **Lead with the step ledger:** for each scenario, "reuses X, Y; needs new step Z." New steps are investment — name them, justify them.
- **Tables over prose.** Use the project's table dialect. Use "any non-nil" matchers for generated ids you can't predict, regex matchers where exactness would be brittle.
- **Make assertions prove the behavior, not just its shape.** Two assertions that together rule out the lazy implementation beat one that a stub could satisfy. Pick the assertion that would fail if someone took the shortcut.
- **Keep it fictional.** Real PII or real use-cases in a spec are a smell — they leak, they date, they read as config. The *behavior* is real; only the *content* is fictional. Use the project's fixture theme.

---

## 8. Anti-patterns (the mistakes to catch)

| Smell | What good looks like |
|---|---|
| "The code probably does X" | Open the file; confirm X. If unchecked, say "unverified." |
| Drafting tables while a design fork is open | Settle the fork first; then the tables are obvious. |
| Reusing a step that's too weak for the assertion | Judge adequacy; propose a precise new step and flag it. |
| `Given` that hand-waves setup | Every precondition the code needs is a real, executable `Given`. |
| Concrete-impl details in a generic component's feature | Test the generic seam; push specifics to the owning module. |
| A wall of scenarios | One or two at a time, numbered, with the step ledger. |
| Inventing fixture names | Reuse the project's established fictional cast. |
| Real PII / real use-cases in a spec | Fictional content, real behavior — always. |
| Decisions left only in chat | Append to the bean, date-stamped, with the why. |
| Silently adding back-compat | Default to clean cutover; make the breaking path explicit. |
| Promoting a bean with no committed scenarios | Scenarios committed `@wip` first; *then* `todo`. |
| "Verify scenario X passes" | Exact runnable command: `bb features features/…:LINE`. |
| Defending a wrong claim | Correct immediately, with the code that proves it. |
| Editing source while planning | Read-only; revert any accidental change. |

---

## 9. Operational hygiene

- **Push after every bean write.** Other agents read these files live; an unpushed decision is invisible (and a stale local copy invites conflicts).
- **Beans live in a shared checkout.** Unstaged changes in `.beans/` or source may be another agent's WIP — describe neutrally, don't stash/rebase over them without asking. Use `--autostash` rebases and check for conflict markers (a committed `<<<<<<<` in a bean's frontmatter breaks the whole `beans` CLI — resolve it before continuing).
- **Bean lifecycle:** `draft` while designing → `todo` once scenarios are committed `@wip` and referenced → workers pick up → `completed` only when `@wip` is removed and scenarios pass.
- **Acceptance must be copy-pasteable:** exact `bb features …:LINE` commands, not "verify it works."

---

## 10. Quick checklist

Before you present scenarios:
- [ ] I read the real code paths — claims are verified, not assumed.
- [ ] The design forks are settled (or asked as crisp either/or).
- [ ] I checked existing steps *and* judged their adequacy; new steps are flagged.
- [ ] Scenarios are at the right abstraction level for this component.
- [ ] Every `Given` is executable; no hand-waved setup.
- [ ] Fixtures reuse the project's fictional cast; no real PII/use-cases.
- [ ] One or two at a time, numbered, with the step ledger.

Before you promote a bean to `todo`:
- [ ] Scenarios written `@wip`, committed, pushed.
- [ ] Bean references the feature file + `file:LINE` selectors.
- [ ] Decisions recorded in the bean, date-stamped, with rationale.
- [ ] Acceptance has exact runnable commands.
- [ ] Clean-cutover stance is explicit (or back-compat consciously chosen).
