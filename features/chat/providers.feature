Feature: Chat with Multiple Providers
  The chat command routes to the correct LLM provider
  based on the agent's model configuration.

  Background:
    Given an empty Isaac state directory "target/test-state"

  Scenario: Chat with Ollama provider
    Given the following models exist:
      | alias       | model           | provider | contextWindow |
      | qwen3-coder | qwen3-coder:30b | ollama   | 32768         |
    And the following agents exist:
      | name | soul           | model       |
      | main | You are Isaac. | qwen3-coder |
    When chat is started with ""
    Then the active provider is "ollama"

  Scenario: Chat with Anthropic provider
    Given the following models exist:
      | alias  | model             | provider  | contextWindow |
      | claude | claude-sonnet-4-6 | anthropic | 200000        |
    And the following agents exist:
      | name | soul           | model  |
      | main | You are Isaac. | claude |
    When chat is started with ""
    Then the active provider is "anthropic"

  Scenario: Chat with OpenAI-compatible provider
    Given the following models exist:
      | alias | model         | provider | contextWindow |
      | grok  | grok-4-1-fast | grok     | 131072        |
    And the following agents exist:
      | name | soul           | model |
      | main | You are Isaac. | grok  |
    And the provider "grok" is configured with:
      | key | value             |
      | api | openai-compatible |
    When chat is started with ""
    Then the active provider is "grok"

  Scenario: Override model switches provider
    Given the following models exist:
      | alias       | model             | provider  | contextWindow |
      | qwen3-coder | qwen3-coder:30b   | ollama    | 32768         |
      | claude      | claude-sonnet-4-6  | anthropic | 200000        |
    And the following agents exist:
      | name | soul           | model       |
      | main | You are Isaac. | qwen3-coder |
    When chat is started with "--model claude"
    Then the active provider is "anthropic"
