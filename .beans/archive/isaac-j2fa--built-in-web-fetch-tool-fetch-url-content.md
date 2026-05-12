---
# isaac-j2fa
title: "Built-in web_fetch tool: fetch URL content"
status: completed
type: feature
priority: low
created_at: 2026-04-20T18:30:22Z
updated_at: 2026-04-20T21:35:53Z
---

## Description

Add a web_fetch tool to retrieve HTML/text from a URL. Often used right after web_search. HTTP(S) only; configurable timeout; returns content + status.

## Acceptance Criteria

1. Implement src/isaac/tool/web_fetch (or inline in isaac.tool.builtin) with HTTP GET, text extraction, and default line limit 2000.
2. Add the two URL-stub step-defs in spec/isaac/features/steps/tools.clj (or wherever the shared tool step-defs land).
3. Remove @wip from all 6 scenarios in features/tools/web_fetch.feature.
4. bb features features/tools/web_fetch.feature passes (6 examples).
5. bb features passes overall.
6. bb spec passes.

## Design

Implementation notes:
- HTTP via org.babashka/http-client (already a dep). GET only for v1.
- Content extraction default: strip <script>, <style>, and HTML comments via regex; collapse whitespace. No full parser — jsoup and hickory don't work in Babashka (bb uses a GraalVM native image with a curated Java class set; neither lib's classes are included).
- format parameter: 'text' (default, strip noise), 'raw' (return body verbatim).
- Default line limit: 2000 (matches read). Dynamic var isaac.tool.web-fetch/*default-limit* for test override.
- Truncation: 'truncated' indicator + total-line hint, same convention as read/grep/glob.
- Binary content-type refusal: if response content-type doesn't start with 'text/', 'application/json', 'application/xml', or 'application/xhtml+xml', return {:isError true :error "binary content-type: <actual>"}.
- Redirects: follow up to 5 hops. 6+ hop chain → error.
- Timeout: 30s default.
- Single-page-app limitation: plain GET won't see JS-rendered content. Documented, accepted; revisit with headless browser if needed.
- No :directories boundary (web_fetch is a network op, not filesystem).

Supporting step-defs this bead will need to add to spec/isaac/features/steps/tools.clj (HTTP stubs via with-redefs on http-client):
1. 'the URL "<url>" responds with:' — table of path/value pairs. Paths: 'status' (default 200), 'header.<name>' (e.g., 'header.content-type', 'header.location'). Stored in a per-scenario map keyed by URL.
2. 'the URL "<url>" has body:' — heredoc body. Sets body for the URL; defaults status=200 and content-type=text/html if not already set.
3. The existing 'the default "<tool>" <key> is <n>' step (shared with glob/grep/read).

## Notes

Implemented built-in web_fetch tool with HTTP GET, redirect following, HTML text extraction, content-type validation, truncation, and feature HTTP stubs. Verification: bb features features/tools/web_fetch.feature, bb features, bb spec, bb spec spec/isaac/tool/builtin_spec.clj.

