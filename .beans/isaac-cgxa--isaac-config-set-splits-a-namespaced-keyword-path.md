---
# isaac-cgxa
title: isaac config set splits a namespaced-keyword path segment (gchat/allow-from) into two nested keys
status: in-progress
type: bug
priority: high
tags:
    - foundation
    - config
created_at: 2026-09-19T23:44:40Z
updated_at: 2026-09-21T03:34:33Z
---

Found 2026-09-19 on yopp: `isaac config set comms.gchat.gchat/allow-from …` and `… comms.gchat.gchat/spaces.spaces/AAQA….respond all` wrote `{:comms {:gchat {:gchat {:allow-from …, :spaces {:spaces {:AAQA… {:respond "all"}}}}}}}` — the / in a namespaced keyword is treated as a path separator — and `config validate` passed, so the stray map sat there silently while the real keys were untouched. Comm extra-schema keys are all namespaced (gchat/…, gmail/…, discord/…), so this affects every comm slot; the feature harness's config table step parses these paths correctly, the CLI does not.

Do: the path parser treats a segment containing / as a namespaced keyword (`gchat/allow-from` → :gchat/allow-from; `spaces/AAQA7rg5Uyc` → :spaces/AAQA7rg5Uyc). Escape hatch if a literal / in a key name is ever needed. Also: a set that would create a key the schema does not know inside a schema'd map (`:gchat` under comms.gchat) should be refused, not written — that is why this stayed invisible.

Scenarios (foundation features/config): set comms.x.x/field writes the namespaced key; set comms.x.x/map.ns/key.field writes nested namespaced keys; get reads them back; a set creating an unknown key under a schema'd map is refused.


## Worker checkpoint 1 (2026-09-21, scrapper@isaac-work-1)

Claimed; investigation complete, no code written yet (nothing to commit on the branch).

**Root cause confirmed.** `c3kit.apron.schema.path/parse` tokenizes on `[a-zA-Z_\-][a-zA-Z0-9_\-]*`, so `gchat/allow-from` becomes TWO `[:key …]` segments (`:gchat`, `:allow-from`) — the `/` is simply dropped by the regex. Three foundation call sites consume it: `src/isaac/config/mutate.clj:110` (parse-config-path — the set/unset writer), `src/isaac/config/schema/resolve.clj:25` and `:35` (schema-for-data-path — what CLI set consults for the value type). The schema side (`isaac.config.nav/path->spec`) splits only on `\.`, so it already treats `gchat/allow-from` as one segment — that asymmetry is exactly why the schema walk says "ok" while the writer splits it in two and `assoc-path` nests `{:gchat {:allow-from …}}`.

**Harness parity confirmed.** The feature-table step (`spec-support/src/isaac/foundation/fs_steps.clj:274-277 isaac-value-path`) does `(mapv keyword (str/split path #"\."))` — slash survives because `(keyword "gchat/allow-from")` → `:gchat/allow-from`. That's the "feature harness parses these correctly, the CLI does not" from the bean.

**Also confirmed while probing:** `path/data-at` (config get) has the same two-segment split, so get can't read a namespaced key back either.

**Plan (next):** TDD. Specs first in `spec/isaac/config/mutate_spec.clj` (and `nav_spec` already covers the nav side). The fix belongs in foundation's own path handling, not c3kit (a vendored mvn lib, 3.0.0): a foundation-side parse that treats a `.`-separated segment containing `/` as one namespaced-keyword segment, feeding BOTH `mutate/parse-config-path` and `schema/resolve` (plus `path/data-at` reads via the CLI get path). Escape hatch already exists in the grammar: `["quoted"]` string segments. Scenarios per the bean into `features/cli/config_resolution.feature`-style harness (need a feature file driving `isaac is run with "config set …"`; check which feature file is the right home).

**Resume from:** `src/isaac/config/mutate.clj:109` (`parse-config-path`), `src/isaac/config/schema/resolve.clj:25`,35; repro scripts at `/tmp/cgxa-repro*.clj` (bb -f).

## Done/next checkpoint (2026-09-21, scrapper@isaac-work-1) — budget wrap-up

**Done (branch `bean/isaac-cgxa`, isaac-foundation, HEAD `2f75cef`, pushed):** `isaac.config.paths/split-path-segments` + `parse-path-segments` keep a `/`-bearing `.`-segment whole as one namespaced keyword; wired into `config/mutate.clj`, `config/schema/resolve.clj`, `config/cli/get.clj`; `config/warnings.clj` renders qualified keys whole. `bb spec -f p` green (1081/0/1984) incl. 4 new `mutate_spec` examples. Acceptance feature `features/cli/config_set_namespaced.feature` committed as wip: 4 examples, **2 failures**.

**Next:** the second grammar site. `src/isaac/config/nav.clj` `path->spec` splits on `.` (fine) but `advance-spec` (nav.clj:19-24) only looks the segment up in `(:schema spec)` — it never consults the berth's dynamic `:extra-schema`, so `relays.helm-station.helm/outer` and `relays.helm-station.helm/freq` fail with "unknown path … (unrecognized segment: helm/outer)". That is why `set --force`/`unset` (which bypass the schema walk) pass while `get` (which goes through nav) fails. Resume at `src/isaac/config/nav.clj:19` — make `advance-spec` resolve a segment against the dynamic-schema-composed spec (see `isaac.config.schema.resolve`), then re-run `bb features features/cli/config_set_namespaced.feature` (expect 4/0) and `bb spec -f p` (expect 1081/0). Open acceptance question for the planner if it survives: the bean's "a set creating an unknown key under a schema'd map is refused, not written" — current behaviour is `:ok` + an `unknown key` warning, not a refusal.
