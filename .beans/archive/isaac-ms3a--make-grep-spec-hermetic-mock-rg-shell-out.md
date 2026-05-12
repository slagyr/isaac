---
# isaac-ms3a
title: "Make grep_spec hermetic — mock rg shell-out"
status: completed
type: task
priority: low
created_at: 2026-05-11T17:25:06Z
updated_at: 2026-05-11T18:21:09Z
---

## Description

isaac.tool.grep shells out to ripgrep (rg). The specs in
spec/isaac/tool/grep_spec.clj call grep-tool end-to-end against real
files with a real rg binary, so they pass or fail based on whether rg
is on PATH. This made bb spec quietly portability-dependent — caught
in CI when 5 scenarios failed before ripgrep was installed on the
runner.

## Approach

1. Add a thin shell-invocation seam in isaac.tool.grep:

       (defn- run-rg [cmd]
         (apply sh/sh cmd))   ; returns {:out :err :exit}

   Today the body of grep-tool calls (apply sh/sh ...) inline; extract
   that single call into run-rg.

2. In grep_spec, with-redefs run-rg to return canned {:out :err :exit}
   maps for each behavioral scenario. The specs now exercise argument
   construction (via grep-command) and result shaping (via grep-result)
   without touching the real binary.

3. Keep one or two integration specs that exercise the real rg path,
   gated on (shell/cmd-available? \"rg\"). These give us actual coverage
   of \"does rg behave the way we think\" where ripgrep is installed,
   and silently skip elsewhere.

## Acceptance

- bb spec passes without ripgrep on PATH (verify by temporarily
  shadowing rg or running in a container without it)
- Behavioral coverage (file/line prefixes, no-matches, glob filter,
  head-limit truncation, count mode) is preserved via mocked specs
- At least one integration spec runs the real rg binary when
  available and is skipped otherwise

## Why not now

Caught in CI; ripgrep is installed in the workflow, so the suite is
green. This is structural cleanup — flag for the next pass on test
quality, not a blocker.

## Notes

Added a shell seam in isaac.tool.grep, converted grep_spec behavioral coverage to mocked rg responses, kept a single availability-gated real-rg integration spec, and verified the spec passes with rg hidden from PATH. Validation: bb spec spec/isaac/tool/grep_spec.clj, bb spec spec/isaac/tool/grep_spec.clj spec/isaac/tool/file_spec.clj spec/isaac/tool/exec_spec.clj spec/isaac/tool/glob_spec.clj, env PATH=<no-rg> /opt/homebrew/bin/bb spec spec/isaac/tool/grep_spec.clj, and full bb spec.

