---
# isaac-6fyn
title: 'isaac-claude-code real smoke: the non-driven ozv9 tool roundtrip fails against claude 2.1.281 (tool never executed) — pre-existing on main'
status: todo
type: bug
priority: low
created_at: 2026-09-24T18:34:30Z
updated_at: 2026-09-24T18:34:30Z
---

Seen 2026-09-24 running `ISAAC_CLAUDE_REAL=1 bb smoke` on the planner box (claude 2.1.281, logged in) against origin/main and against isaac-mbnb alike: `claude_cli_real_spec` case "executes exec and replies from the unguessable result, not a prediction" fails at `(should @executed?)` — the exec tool-fn is never called. That case drives the CLI in NON-driven mode (`cfg {:command "claude"}`, default tool loop), where the CLI runs with `--tools ""` and no MCP config, so the model has no tool to call and answers in text; the other two real cases pass, and a driven roundtrip (HTTP MCP, isaac-mbnb) passes.

Decide: either the case should run driven (`:drives-tool-loop? true`, the module's production mode — then it duplicates the mbnb smoke and could be adopted as the permanent real smoke), or the non-driven text-fenced tool-call path (isaac-zz6d fallback) is what it means to test, in which case the expectation about `executed?` needs the fence parser in the loop. Pick one and make the smoke honest; `bb smoke` should be green on a logged-in box.

Repo scope: isaac-claude-code (`spec/isaac/llm/claude_cli_real_spec.clj`).
