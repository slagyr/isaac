---
# isaac-7q4c
title: 'provider__quota: subscription quota per provider (berth, tool, CLI, chatgpt + grok meters)'
status: todo
type: task
priority: normal
tags:
    - agent
created_at: 2026-10-28T07:23:35Z
updated_at: 2026-10-28T07:23:35Z
---

## Why

Crews and people should be able to see how much of each subscription is left — the Claude, ChatGPT and Grok plans the providers run on — without opening three vendor dashboards. Ruling (Micah, 2026-10-28): a tool crews can call, plus a CLI; meters register per provider through a berth so a fragile vendor endpoint can move into its own module later.

## What

### The berth: `:isaac.agent/quota-meter`

Keyed by provider **template type** (`:chatgpt`, `:grok`, later `:claude-code`) — not by `:api`, since a `:grok` provider may run on `"responses"`. Value: a factory resolving to a meter fn `(fn [provider-config] → report)`. Each configured provider (config `:providers` entity) resolves its template type; no meter ⇒ `:unsupported`.

Agent wraps every meter call: timeout, conform against the report schema, exceptions and non-conforming reports ⇒ `:status :unknown` with `:error`. Reports are cached per provider id for **2 minutes** (clock-driven, so `the current time is` moves it).

### The report schema (c3kit-schema, in agent)

Report: `:provider` (id), `:status` (`:ok` | `:unknown` | `:unsupported`), `:checked-at` (inst), `:plan` (optional string), `:windows` (seq, `[]` when none), `:balances` (optional seq), `:error` (only with `:unknown`).

Window — a limit that resets: `:id` (keyword, the provider's own name), `:label` (string for people), `:used-percent` (double 0–100, **required**), `:resets-at` (inst, required), `:starts-at` (optional), `:length-seconds` (optional), `:limited?` (bool), `:scope` (optional map, e.g. `{:product "GrokBuild"}`), `:used`/`:limit`/`:unit` (optional — only when a provider gives real amounts; none does today).

Balance — credits or money that don't reset: `:id`, `:amount` (double), `:unit` (keyword), `:cap`/`:used` optional.

Labels: derived from the window length — under 48 h ⇒ `"<n>-hour"`, else `"<n>-day"` (18000 s ⇒ `5-hour`, 604800 s ⇒ `7-day`).

Remaining is never stored: it is 100 − used-percent.

### The tool: `provider__quota`

Optional param `provider` narrows to one provider id; without it, every configured provider, ordered by id. Returns JSON `{"providers": [report …]}` with snake_case keys (`used_percent`, `resets_at`, `checked_at`, `length_seconds`, `limited`, `starts_at`). Never an error result because a meter failed — that is the report's `status`. Registered like the other built-ins (granted per crew).

### The CLI: `isaac quota [--provider <id>]`

Hosted. Same reports, one line per window: `PROVIDER  WINDOW  USED  RESETS`, used as a whole percent (`42%`), reset as `YYYY-MM-DD HH:MM UTC`. Unsupported/unknown providers get one line saying so (with the reason for unknown).

### Meters in agent

- **ChatGPT** (`:chatgpt`): `GET https://chatgpt.com/backend-api/wham/usage`, headers `Authorization: Bearer <oauth access>` and `chatgpt-account-id: <account>` (same token + account resolution the responses api uses, including refresh). `rate_limit.primary_window` / `secondary_window` ⇒ windows `:primary`/`:secondary` (`used_percent`, `limit_window_seconds`, `reset_at` epoch seconds; `limited?` from `rate_limit.limit_reached`/`allowed`); `plan_type` ⇒ `:plan`; `credits.balance` (string) ⇒ balance `:credits`. A null secondary window is omitted.
- **Grok** (`:grok`): `GET https://cli-chat-proxy.grok.com/v1/billing?format=credits`, `Authorization: Bearer <oauth access>` (Isaac's grok OAuth client is the same one the grok CLI uses). `config.currentPeriod` + `creditUsagePercent` ⇒ window `:weekly` (start/end ⇒ starts-at/resets-at, length from the two); each `productUsage[]` ⇒ a window with `:scope {:product …}`, same period; a missing percent (proto3 drops zeros) ⇒ `0.0`, same for a missing `creditUsagePercent`; `prepaidBalance.val` ⇒ balance `:prepaid`.

Both endpoints are undocumented vendor internals — that is why failure is a status.

## Acceptance

- Scenarios: isaac-agent `features/tool/provider_quota.feature` (6 @wip; gate lines below).
- New step `oauth logins exist:` (table: provider, access-token, account-id) seeding the auth store so each provider's OAuth access resolves without refresh.
- The meters' HTTP calls go through the stubs `the URL … responds with:` / `has body:` set (today those wrap web tools only — extend them to meter calls and to the CLI run), and are recorded so `an outbound HTTP request to "…" matches:` sees them.
- Header keys in the request matcher are as sent (`headers.Authorization`, `headers.chatgpt-account-id`).
- `bb features` and `bb jvm-features` green.

## Out of scope

- The Claude meter — isaac-claude-code, the follow-on bean (blocked by this one).
- Using quota to steer model fallback.
- Moving chatgpt/grok out of agent.

## Planner notes

- Live shapes, checked 2026-10-28 with real logins: Claude `/api/oauth/usage` → `five_hour`/`seven_day` `{utilization (percent), resets_at}`; ChatGPT `wham/usage` → `plan_type`, `rate_limit.primary_window {used_percent, limit_window_seconds, reset_after_seconds, reset_at}`, `secondary_window` (null on some plans), `credits {has_credits, balance "0"}`; Grok billing → `config.currentPeriod {type, start, end}`, `creditUsagePercent`, `productUsage [{product, usagePercent?}]`, `prepaidBalance {val}`, `onDemandCap/Used`.
- Undefined steps run as no-ops in this harness, so the scenarios fail today at the first tool assertion, not at `oauth logins exist:`.

feature-baseline: isaac-agent c7467252ff6c888ec578b6bd21c3f7ee51381ab8
feature-blob: isaac-agent features/tool/provider_quota.feature 7d7bcbb9aeb08614c68bb809f348e5c3399fae83
