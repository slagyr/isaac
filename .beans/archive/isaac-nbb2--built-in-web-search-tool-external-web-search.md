---
# isaac-nbb2
title: "Built-in web_search tool: external web search"
status: completed
type: feature
priority: low
created_at: 2026-04-20T18:30:21Z
updated_at: 2026-04-20T21:44:11Z
---

## Description

Add a web_search tool that calls an external search API (Tavily, Brave, or similar). Global scope — not tied to project directories. Provider/API-key configured in isaac.edn. Returns top-N results with title/url/snippet.

## Acceptance Criteria

1. Implement src/isaac/tool/web_search with Brave provider and default num_results 5.
2. Add the 'the search query "<q>" returns results:' step in spec/isaac/features/steps/tools.clj.
3. Remove @wip from all 4 scenarios in features/tools/web_search.feature.
4. bb features features/tools/web_search.feature passes (4 examples).
5. bb features passes overall.
6. bb spec passes.

## Design

Implementation notes:
- Provider: Brave Search API (independent index, not Google-backed). Endpoint: GET https://api.search.brave.com/res/v1/web/search?q=<query>&count=<n>. Auth: X-Subscription-Token header.
- Config: root :tools :web_search {:provider :brave :api-key "${BRAVE_API_KEY}"}. Use existing ${VAR} substitution. Config is outside crew reach, so keys stay safe.
- Parameters: query (required), num_results (optional, default 5).
- Output shape: numbered list, one block per result — '1. <title>\n   <url>\n   <description>\n\n2. ...'. Readable for LLM, pairs naturally with web_fetch.
- No results: {:result "no results"} (not an error). Same convention as grep/glob.
- Missing api-key: {:isError true :error "web_search not configured: set :tools :web_search :api-key (e.g. ${BRAVE_API_KEY})"}. Fail fast with guidance per the 'deceptive default fallbacks' planning rule.
- Network/HTTP failure: {:isError true :error "<http error>"}. Same pattern as web_fetch.
- Logging: emit :tool/exec with query logged. Queries are not redacted — consistent with other tool logs.

Supporting step-def this bead adds to tools.clj:
- 'the search query "<q>" returns results:' — table (title/url/description columns) → synthesized Brave JSON response mapped to the query. Stub via with-redefs on the http-client. The step implicitly provides an :api-key in config so the 'not configured' scenario can be distinguished.

## Notes

Implemented built-in web_search tool with Brave provider support, config-driven API key lookup, formatted numbered results, and feature-side Brave query stubs. Verification: bb features features/tools/web_search.feature, bb features, bb spec, bb spec spec/isaac/tool/builtin_spec.clj.

