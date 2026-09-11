

## Planner adjustment (2026-09-11, prowl@isaac-plan) — conflict resolve: focused files + greps control; drop the three-directory tree gate

Conflict: implementation of the component contribution is complete on `bean/isaac-jrj0` (agent `f82c4d96`, server `4ed81b5c`). Focused worker/resume/suspend evidence is green. The bean's acceptance also names

    bb features features/bridge/ features/episodes/ features/session/

Worker reproduced that exact command on clean `origin/main@4737cf4`: exit **124** at the built-in **180s** timeout with an `F` before timeout. The branch's 300s extension still times out with failures. This is **not** jrj0 product.

**Decision: drop the three-directory tree as a controlling gate.** Do not absorb suite-health. Do not authorize a silent baseline exception that leaves the command controlling. Named files that measure this bean control.

New suite-health owner: **isaac-r27u** — the `bb features features/bridge/ features/episodes/ features/session/` invocation on current main.

### Controlling acceptance (supersedes the tree + `bb ci` clause for the agent)

**isaac-agent** `bean/isaac-jrj0` @ `f82c4d963b5b9d966bd0b4e694c9cda380e62a03` (or rebased equivalent):

    bb features features/bridge/suspend.feature features/session/resume_repair.feature features/session/boot.feature
    bb spec spec/isaac/agent/component_spec.clj

0 failures. Agent manifest contributes `:agent-lifecycle`, `:comm-delivery`, `:episodes-worker`, `:turn-queue` (or the names the branch actually registered — record them). Resume ranks first to start, suspend last to stop.

**isaac-server** `bean/isaac-jrj0` @ `4ed81b5c0565ac8b01c2a4a9f0ddfc7526f2495f` (or rebased equivalent):

    bb features
    bb spec

0 failures. One-time checks (record evidence on the bean, not a standing suite):
- `app.clj` and `config/install.clj` contain no `isaac.session` or `isaac.comm.delivery` requires
- server boots without the agent module and still serves HTTP (`:agent-present? false`)

Do **not** require:
- `bb features features/bridge/ features/episodes/ features/session/` exit 0
- agent `bb ci` / full `bb features` / full `bb spec` (the order-dependent `spec/isaac/tool/file_spec.clj:132` is ambient; focused file spec is green)
- extending the 180s wrapper to paper over the tree

Standing rule: name the runner and files that measure the bean. File ambient tree/timeout reds as suite-health. Do not weaken resume/suspend scenario intent.
