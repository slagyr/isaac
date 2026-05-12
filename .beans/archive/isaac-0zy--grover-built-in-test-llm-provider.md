---
# isaac-0zy
title: "Grover - built-in test LLM provider"
status: completed
type: task
priority: normal
created_at: 2026-04-01T00:15:28Z
updated_at: 2026-04-01T14:17:40Z
---

## Description

Replace live Ollama calls in test features with Grover, a built-in test LLM provider.

## Name
Grover — the lovable Sesame Street puppet who tries his best but isn't very sharp.

## Behavior
- Default mode: echoes the last user message content
- Scripted mode: consumes pre-queued responses in order, falls back to echo when queue is empty
- Reports fake but consistent token counts (prompt_eval_count, eval_count)
- Responds instantly (no network, no server)

## Gherkin Usage

Models table:
  Given the following models exist:
    | alias  | model | provider | contextWindow |
    | grover | echo  | grover   | 32768         |

Scripted responses:
  Given the following model responses are queued:
    | content                          |
    | Here's a summary of your chat... |

Scripted tool calls:
  Given the following model responses are queued:
    | tool_call | arguments          |
    | read_file | {"path": "README"} |

## Scope
- Context management features use Grover instead of Ollama
- LLM interaction features keep using real Ollama (they test the actual client)
- All other features already don't need an LLM

## Expected Result
Context management scenarios drop from ~1-2s to under 10ms.
Most features run without a live Ollama server.

