
## Worker notes (scrapper@isaac-work-1, 2026-09-25)

Implemented on isaac branch `bean/isaac-ctsf` (head 693246ce, not yet on main):
- `core/baseline` refuses any status but draft/todo (`:refused?` → exit 2, file untouched) and sets front-matter `status: todo` when it writes the gate lines (`bean/with-status`).
- `bb bean-gate baseline <id>` with no refs → exit 2 "a bean is baselined against scenarios; none given".
- New `bb bean-gate ready <id>` (`core/ready`): 0 todo+baselined; 1 "status <s>" / "not baselined" / "no bean file"; 2 no id. Pure read.
- Specs: new `spec/isaac/bean_gate/main_spec.clj` (12 examples) + `with-status` in bean_spec. `bb spec`: 76 examples, 0 failures. clj-kondo: no new findings (only pre-existing speclj-macro / fixture / `short` noise).
- Docs carry the sentence: isaac/AGENTS.md (Bean Workflow status flow + Baseline the bean step 2/5), isaac/.toolbox/commands/plan.md (step 3 now baselines; step 4 ready check), ~/agents/isaac/plan/AGENTS.md (created — planner readiness rule). hail-bean-plan skill (~/.isaac/prompts/skills, commit bd51dbe in ~/.isaac) links to plan/AGENTS.md and runs `ready` before work hails.

Observations: `.toolbox/commands/plan.md` is a toolbox copy of agent-lib's plan.md — a toolbox update would clobber step 3/4. ~/.isaac/prompts/commands/plan.md (generic, what hail-bean-plan's bootstrap names) still says `--status=todo`; left alone as it is shared with non-gated projects.
