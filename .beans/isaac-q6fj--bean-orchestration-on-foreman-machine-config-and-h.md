---
# isaac-q6fj
title: 'Bean orchestration on Foreman: the bean-work machine (happy path and failure rows)'
status: completed
type: feature
priority: normal
created_at: 2026-09-27T23:09:07Z
updated_at: 2026-10-28T08:20:51Z
parent: isaac-q3u3
---

Likely repos: **orchestration** (machine config, crew prompts/skills, `bb test-machines`) and **isaac** (bean workflow references). Merged 2026-09-29 (Micah + planner): isaac-1rtr (failure paths) folded in.

## Ungated

The orchestration repo has no feature runner, so this bean carries no Gherkin baseline. Its acceptance is machine tests run by `isaac foreman test` (isaac-1qgv) plus live runs on zanebot. It adds **no code to Isaac** — config, prompts, skills, and docs only; no legacy behavior.

## Contract

- A **bean-work Foreman machine** (`config/machines/bean-work.edn` in orchestration): planned/todo → claimed work → gated completion or ungated verification → return for repair → held / human escalation → completed. Preserve the gated-vs-ungated bean rule (isaac/AGENTS.md).
- Work turns are `:turn` actions against a logical session pattern (`:frequencies`) and the Worksite pool (`:resource-pools`); Foreman never names a session or directory.
- **Failure rows** (from isaac-1rtr): verification failure returns to work; all worksite members busy → the turn waits (Agent), no machine change; restart mid-handoff resumes without a duplicate turn (idempotency keys); a turn that ends without its crew signal takes a `:turn-ended` backstop row (nudge / re-dispatch / escalate); a provider-auth outage parks the turn (Agent weather) and the bean shows as waiting, not failed; human escalation → `held`, visible in `isaac foreman list --state held`.
- **Crew side:** worker and verifier prompts/skills call `foreman__signal` (e.g. `:landed`, `:verified`, `:repair`, `:blocked`) instead of hailing the next role. Planner dispatch = `isaac foreman start bean-work <bean-id>`.
- **Runs alongside the hail-dispatch path** during rollout: only beans explicitly started on the machine use it. The overlap is config-only and ends in isaac-20gd.

## Acceptance

- [ ] `bb test-machines` in orchestration passes: cases for the happy path (gated and ungated), repair loop, missing signal backstop, escalation to held, and an unexpected event.
- [ ] Live on zanebot: one gated bean goes todo → completed through Foreman with a Worksite pool; one ungated bean through verification; `isaac foreman status` / `history` name the current step and owner at each point.
- [ ] Live on zanebot: a deliberately failed verification returns the bean to work and it then completes.
- [ ] No Isaac source changes; config validates (`isaac config validate`) on zanebot.

Blocked by isaac-1qgv (machine tests) and isaac-npmp/isaac-lr8h (landed).


## Re-scope (2026-10-21, Micah + planner)

**What already exists.** zanebot runs a pilot `isaac-bean-work` machine at
`~/.isaac/config/machines/isaac-bean-work.edn`, in no repo. It carried
isaac-8uno (10-06) and isaac-htix (10-07) to `landed`. It is gated-only:
`dispatch` → `:work` turn (scrapper, session `bean-<id>`, pool
`isaac-worksites`, `:output :event`), the reply's last line is `event: landed`
or `event: conflict <reason>`, and conflict / quiet / failed / dead turns stall
with a prowl `:alert` turn. Crew side: `isaac/.toolbox/skills/foreman-bean-work`.

**Decisions (Micah, 2026-10-21):**

- Events come from the reply's last line (`:output :event`), as the pilot
  proved, not from a `foreman__signal` tool call. The Contract bullet above
  that names `foreman__signal` is superseded.
- Ungated path: the worker ends `event: unverified`; a perceptor `:verify` turn
  (session `verify-<id>`, same pool) ends `event: verified` or `event: repair`;
  repair re-runs `:work`.
- Stalls remember their side: `stalled` (worker) and `verify-stalled`
  (verifier); `dispatch` resumes the matching turn. `abandon` from any state.
- The machine moves into orchestration at
  `isaac-beans/config/machines/isaac-bean-work.edn`, deployed by
  `isaac-beans/install.sh` alongside `config/hail/`.

**Contract:** `orchestration/isaac-beans/features/isaac-bean-work.feature`
(orchestration 026ec07), 16 machine tests. Do not change its scenarios; raise
a mismatch instead. Machine tests carry no instance data, so `{{data.*}}`
renders empty there.

**Work:**

1. `isaac-beans/config/machines/isaac-bean-work.edn`. The reference below
   passes all 16 tests (checked 2026-10-21 through isaac-foreman's own
   machine-test harness on main e7f20a7).
2. `install.sh` copies `config/machines/` too.
3. A `bb.edn` at the orchestration root with `test-machines`, running
   `isaac foreman test` on `isaac-beans/features/*.feature` against a fixture
   Isaac root that holds the machine. Watch out: a bare root does not load. The
   machine names crews scrapper / perceptor / prowl and pool `isaac-worksites`,
   and `config validate` needs a provider plus the foreman, worksite and http
   modules installed in that root. Document the one-time setup in the
   orchestration README.
4. Crew side, in `isaac/.toolbox/skills/`:
   - `foreman-bean-work`: gate exit 2 (not gated) means take the ungated close:
     leave the bean `in-progress` + `unverified`, end `event: unverified`
     (today it says `event: conflict not gated`).
   - New `foreman-bean-verify`: the verify craft of `hail-bean-verify` / `/verify`
     with no hails. Pass: remove the tag, `completed`, end `event: verified`.
     Fail: the `## Verification failed` note, `--status=in-progress
     --remove-tag=unverified`, end `event: repair`. A blocker only a person
     can clear: `event: conflict <reason>`.
   Quote every SKILL.md `description:` (see the skill frontmatter YAML trap).
5. Deploy to zanebot (`install.sh`, then `isaac config validate`). Config
   hot-reloads; no restart.

### Reference machine

```clojure
;; One instance per bean; the instance id IS the bean id. Dispatch with the
;; bean's title (the bean stays the source of truth; the title is the
;; worker's orientation and episodes' recall query):
;;   isaac foreman start isaac-bean-work <bean-id> --data '{:title "<bean title>"}'
;;   isaac foreman signal isaac-bean-work <bean-id> dispatch
;; Foreman writes the mechanics into each turn's preamble (machine, instance,
;; state, and the events the turn may end with).
;;
;; Gated beans land from :working. Ungated beans go to a verifier, which
;; either completes them or sends them back to work. A stall remembers which
;; side it came from, so `dispatch` resumes the right turn.
{:initial :queued
 :actions
 {:work   {:type           :turn
           :frequencies    {:crew "scrapper" :session "bean-{{instance}}" :create :if-missing}
           :resource-pools ["isaac-worksites"]
           :output         :event
           :prompt         "Work bean {{instance}}: {{data.title}}\n\nFollow the foreman-bean-work skill. The bean is the source of truth: read it with `beans show {{instance}}` and keep your notes there."}

  :verify {:type           :turn
           :frequencies    {:crew "perceptor" :session "verify-{{instance}}" :create :if-missing}
           :resource-pools ["isaac-worksites"]
           :output         :event
           :prompt         "Verify bean {{instance}}: {{data.title}}\n\nFollow the foreman-bean-verify skill. The bean is the source of truth: read it with `beans show {{instance}}` and record your verdict there."}

  :alert  {:type        :turn
           :frequencies {:crew "prowl" :session "plan-{{instance}}" :create :if-missing}
           :prompt      "Bean {{instance}} ({{data.title}}) needs attention. Reason, if one was given: {{data.reason}}. Check `isaac foreman status isaac-bean-work {{instance}}` and the bean, then tell Micah what you found."}}

 :transitions
 [{:start :queued         :event :dispatch            :end :working        :actions [:work]}
  ;; the worker's reply names the outcome
  {:start :working        :event :landed              :end :landed}
  {:start :working        :event :unverified          :end :verifying      :actions [:verify]}
  {:start :working        :event :conflict            :end :stalled        :actions [:alert]}
  {:start :working        :event :foreman/turn-ended  :end :stalled        :actions [:alert]}
  {:start :working        :event :foreman/turn-failed :end :stalled        :actions [:alert]}
  {:start :working        :event :foreman/turn-died   :end :stalled        :actions [:alert]}
  ;; the verifier's reply names the verdict
  {:start :verifying      :event :verified            :end :landed}
  {:start :verifying      :event :repair              :end :working        :actions [:work]}
  {:start :verifying      :event :conflict            :end :verify-stalled :actions [:alert]}
  {:start :verifying      :event :foreman/turn-ended  :end :verify-stalled :actions [:alert]}
  {:start :verifying      :event :foreman/turn-failed :end :verify-stalled :actions [:alert]}
  {:start :verifying      :event :foreman/turn-died   :end :verify-stalled :actions [:alert]}
  ;; a person (or the planner) resumes or gives up
  {:start :stalled        :event :dispatch            :end :working        :actions [:work]}
  {:start :verify-stalled :event :dispatch            :end :verifying      :actions [:verify]}
  {:start :*              :event :abandon             :end :abandoned}]}
```

## Acceptance (2026-10-21; supersedes the list above)

- [ ] From a clean orchestration clone, following the README: `bb test-machines` prints 16 `PASS` lines and exits 0.
- [ ] `isaac-beans/install.sh --dry-run` lists `config/machines/isaac-bean-work.edn`.
- [ ] On zanebot after install: `isaac config validate` is clean and `isaac foreman list isaac-bean-work` still shows isaac-8uno and isaac-htix as `landed`.
- [ ] Live: one gated bean goes `queued` → `landed` through the machine.
- [ ] Live: one ungated bean goes through `verifying` to `landed`.
- [ ] Live: a deliberately failed verification returns the bean to `working` (via `repair`), and it then lands.
- [ ] No Isaac source changes.



## Worker checkpoint (2026-10-21)

Done: orchestration bean branch adds machine, isolated bb test-machines (16 PASS), installer and README; isaac bean branch adds Foreman ungated work and verify skills. Both pushed. Deployment on zanebot preserves isaac-8uno and isaac-htix as landed. Next: resolve YAML syntax in orchestration hail headers (config/parse.clj:169); re-run isaac config validate, then live Foreman acceptance runs for gated, ungated and repair. Current test: bb test-machines 16 PASS; config validate reports hail/orchestration-harden.md and hail/orchestration-verify.md YAML syntax errors. Gate: exit 2 (ungated). Resume at isaac-beans/config/hail/orchestration-verify.md:1.



## Live acceptance blocker (2026-10-21)

On zanebot, deployed the Foreman machine with isaac-beans/install.sh; isaac config validate --edn reports :ok true and no errors (warnings are pre-existing). isaac foreman list isaac-bean-work still shows isaac-8uno and isaac-htix landed. bb test-machines produces 16 PASS; dry-run lists machines/isaac-bean-work.edn. Skills are copied into ~/.isaac/prompts/skills/foreman-bean-{work,verify}. The three live acceptance runs (gated queued -> landed; ungated verifying -> landed; deliberate verify repair -> landed) remain unrun. This bean was dispatched on the hail path, and no dedicated todo gated fixture with a planner-owned feature baseline was supplied; a worker cannot create a feature baseline. Starting this hail-owned bean on Foreman would violate opt-in exclusive dispatch. Need planner to provide/authorize a dedicated gated bean and ungated test beans for live machine runs, or adjust acceptance if live runs are deferred. Keep branch bean/isaac-q6fj in both isaac and orchestration; resume at orchestration/isaac-beans/config/machines/isaac-bean-work.edn:6 and README.md:37 after fixtures are assigned. Gate output: isaac-q6fj: no feature-baseline: use the verify path (exit 2).

## Live acceptance (2026-10-28, planner)

All three live runs went through the deployed machine on zanebot:

- **Gated:** isaac-166j queued → working → landed in 10 min (isaac-claude-code dbfd134). isaac-mo02 also went gated, through a `conflict` stall and a planner `dispatch` back to working, then landed (isaac-agent eae0cdf, isaac-foreman fed1e6e).
- **Ungated with repair:** isaac-v3qp went working → verifying → working (`repair`) → verifying → landed in 9 min (orchestration 18c649c).
- **Earlier instances intact:** `isaac foreman list isaac-bean-work` still shows isaac-8uno and isaac-htix `landed`.

Found and fixed along the way:

- isaac-pqxn (the first ungated try, scrapped) exposed isaac-mo02: a stale `:alert` turn's quiet end moved verifying → verify-stalled, and the verifier's correct `repair` was dropped. Fixed in Foreman 0.6.1 (fed1e6e), deployed 2026-10-28.
- The worker put its ungated handoff on an isaac `bean/<id>` branch, so main never showed it. Planner fixed both Foreman skills on this branch (92e95dae): the handoff goes to isaac main, and the verifier ends with `event: conflict` when it finds none. Deployed to `~/.isaac/prompts/skills/` on zanebot (backups `SKILL.md.bak-20261028`). The v3qp rerun used the fixed skills.

Still for the verifier: `bb test-machines` from a clean orchestration clone, `isaac-beans/install.sh --dry-run`, and that this branch changes no Isaac source (only `.toolbox/skills/foreman-bean-*`). Land orchestration `bean/isaac-q6fj` (3ae4008) and isaac `bean/isaac-q6fj` (92e95dae).



## Verify fail (attempt 1, 2026-10-28): isaac bean branch conflicts with current main during required pre-landing rebase

HEAD: isaac main 6a7688be8839664f8595c55b63ea6ab224b829b6; bean/isaac-q6fj 461bd94d801c7be1bdf74f387c5414b0b31ae3df.
Working tree: clean after abort; no implementation was landed.

Reproduction: in a fresh isaac worktree on bean/isaac-q6fj, `git fetch origin && git rebase origin/main` conflicts while replaying f53929a9 (checkpoint done and next) in `.beans/isaac-q6fj--bean-orchestration-on-foreman-machine-config-and-h.md`. Per hail-bean-verify section 7a, conflicts are a FAIL and must be resolved by the worker before landing; I ran `git rebase --abort`. Rebase or otherwise reconcile the bean branch against current isaac origin/main, push the repaired branch, preserve the latest bean body, then return for verification. Orchestration branch rebased locally cleanly, but not landed or pushed.

Other checks already green: clean orchestration clone `bb test-machines` produced exactly 16 PASS lines and exit 0; `isaac-beans/install.sh --dry-run` with .env HOST=localhost USER=zane listed `machines/isaac-bean-work.edn` and exited 0; `isaac config validate --edn` reports :ok true with no errors; foreman list/status show isaac-166j gated landed, isaac-v3qp ungated repair then landed, isaac-8uno and isaac-htix intact landed. No Isaac src/ change in bean diff. The initial dry-run without .env failed as documented; repeated with .env and passed.

## Verify repair (attempt 1, 2026-10-28)

Rebased isaac `bean/isaac-q6fj` onto origin/main abec9601 and pushed 8d846bdc. The already-mirrored checkpoint, blocker and live-acceptance bean commits were skipped during replay to preserve the latest main bean body, including the verify-fail note and unverified status. The branch diff is only `.toolbox/skills/foreman-bean-{work,verify}/SKILL.md`; no Isaac source changes. Rebased orchestration `bean/isaac-q6fj` onto origin/main 18c649c and pushed 1860dfc. `bb bean-gate verify isaac-q6fj` exits 2 (ungated); orchestration `bb test-machines` prints 16 PASS, exit 0; installer `--dry-run` lists `machines/isaac-bean-work.edn`, exit 0. Both branches clean and fast-forwardable at these bases. Ready for re-verification.



## Landed on main (2026-10-28)

main-sha: orchestration b1359c8e1d99b1614bbe2dc3034c22026778a835
main-sha: isaac 55e256ffb5e62d03b3f407342f11ab47894748e7
