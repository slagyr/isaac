---
# isaac-4dc
title: "Server skeleton with /status endpoint"
status: completed
type: feature
priority: high
created_at: 2026-04-08T16:39:25Z
updated_at: 2026-04-08T18:09:57Z
---

## Description

Build the first HTTP server slice with reload-friendly structure and a simple JSON status endpoint.

## Scope
- Add server entry point/lifecycle namespace using c3kit.apron.app
- Add http namespace that builds top-level handlers and middleware
- Add routes namespace that maps endpoints to handlers
- Add status handler/business logic for GET /status
- Return HTTP 200 with JSON body listing services and their statuses
- Add specs for server/http/routes/status namespaces
- Add feature coverage for /status

## Structure goals
- Keep HTTP concerns separate from business logic
- Make route wiring explicit in routes.clj
- Keep handler construction reload-friendly for development
- Prepare for future config hot-reload behavior

## Notes
- Start with a shallow service status report, not deep health checks
- This bead can link to the dual-runtime server architecture epic isaac-dps

