Feature: Chat Options
  The chat command supports options to specify agent, model,
  and session resumption.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias       | model           | provider | contextWindow |
      | grover      | echo            | grover   | 32768         |
      | qwen3-coder | qwen3-coder:30b | ollama   | 32768         |
    And the following agents exist:
      | name       | soul                  | model       |
      | main       | You are Isaac.        | grover      |
      | researcher | You are a researcher. | grover      |

  Scenario: Default agent and model
    When chat is started with ""
    Then the active agent is "main"
    And the active model is "echo"
    And the active provider is "grover"

  Scenario: Specify agent
    When chat is started with "--agent researcher"
    Then the active agent is "researcher"
    And the active soul contains "researcher"

  Scenario: Override model via command line
    When chat is started with "--model qwen3-coder"
    Then the active model is "qwen3-coder:30b"
    And the active provider is "ollama"

  Scenario: Override model resolves alias to different provider
    Given the following models exist:
      | alias  | model             | provider  | contextWindow |
      | claude | claude-sonnet-4-6 | anthropic | 200000        |
    When chat is started with "--model claude"
    Then the active model is "claude-sonnet-4-6"
    And the active provider is "anthropic"
    And the context window is 200000

  Scenario: Resume picks up the latest session
    Given the following sessions exist:
      | key                              | updatedAt     |
      | agent:main:cli:direct:testuser   | 1000000000000 |
      | agent:main:cli:direct:testuser2  | 2000000000000 |
      | agent:main:cli:direct:testuser3  | 1500000000000 |
    When chat is started with "--resume"
    Then the active session is "agent:main:cli:direct:testuser2"

  Scenario: Session flag resumes a specific older session
    Given the following sessions exist:
      | key                              | updatedAt     |
      | agent:main:cli:direct:testuser   | 1000000000000 |
      | agent:main:cli:direct:testuser2  | 2000000000000 |
    When chat is started with "--session agent:main:cli:direct:testuser"
    Then the active session is "agent:main:cli:direct:testuser"

  Scenario: Context window resolved from model config
    Given the following agents exist:
      | name | soul           | model       |
      | main | You are Isaac. | qwen3-coder |
    When chat is started with ""
    Then the context window is 32768
