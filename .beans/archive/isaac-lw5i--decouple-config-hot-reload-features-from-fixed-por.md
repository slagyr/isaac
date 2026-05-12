---
# isaac-lw5i
title: "Decouple config hot-reload features from fixed port binding"
status: completed
type: bug
priority: normal
created_at: 2026-04-28T15:24:54Z
updated_at: 2026-04-28T16:19:55Z
---

## Description

The config hot-reload feature scenarios currently fail when another Isaac server is already running because they start a real http-kit server on a fixed port. The hot-reload behavior under test does not require binding a real network port. Update the implementation and/or feature support so the config hot-reload features can exercise reload behavior without binding to a fixed port, and ensure the relevant specs/features pass hermetically.

