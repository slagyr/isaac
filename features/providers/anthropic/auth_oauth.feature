Feature: Anthropic OAuth Authentication
  Isaac authenticates with the Anthropic Messages API using
  OAuth credentials from Claude Code's cached login.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model             | provider  | contextWindow |
      | claude | claude-sonnet-4-6 | anthropic | 200000        |
    And the following agents exist:
      | name | soul           | model  |
      | main | You are Isaac. | claude |

  # --- Credential Reading ---

  @wip
  Scenario: Read OAuth token from Claude Code credentials file
    Given a Claude Code credentials file exists with:
      | key          | value              |
      | accessToken  | test-access-token  |
      | refreshToken | test-refresh-token |
      | expiresAt    | 9999999999999      |
    And the provider "anthropic" is configured with:
      | key  | value |
      | auth | oauth |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When the prompt is sent to the LLM
    Then the request header "Authorization" is "Bearer test-access-token"

  @wip
  Scenario: No Claude Code credentials found
    Given no Claude Code credentials file exists
    And the provider "anthropic" is configured with:
      | key  | value |
      | auth | oauth |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When the prompt is sent to the LLM
    Then an error is reported indicating no OAuth credentials found

  # --- Token Refresh ---

  @wip
  Scenario: Expired token is refreshed automatically
    Given a Claude Code credentials file exists with:
      | key          | value              |
      | accessToken  | expired-token      |
      | refreshToken | valid-refresh      |
      | expiresAt    | 1000000000000      |
    And the provider "anthropic" is configured with:
      | key  | value |
      | auth | oauth |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When the prompt is sent to the LLM
    Then the OAuth token was refreshed
    And the request header "Authorization" matches #"Bearer .+"

  @wip
  Scenario: Refresh token is invalid
    Given a Claude Code credentials file exists with:
      | key          | value              |
      | accessToken  | expired-token      |
      | refreshToken | invalid-refresh    |
      | expiresAt    | 1000000000000      |
    And the provider "anthropic" is configured with:
      | key  | value |
      | auth | oauth |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content |
      | user | Hello   |
    When the prompt is sent to the LLM
    Then an error is reported indicating OAuth refresh failed

  # --- Integration ---

  @wip @slow
  Scenario: Live OAuth authentication via Claude Code credentials
    Given Claude Code is logged in
    And the provider "anthropic" is configured with:
      | key  | value |
      | auth | oauth |
    And the following sessions exist:
      | key                         |
      | agent:main:cli:direct:user1 |
    And the following messages are appended:
      | role | content     |
      | user | Say "hello" |
    When the prompt is sent to the LLM
    Then the transcript has entries matching:
      | type    | message.role | message.provider |
      | message | assistant    | anthropic        |
