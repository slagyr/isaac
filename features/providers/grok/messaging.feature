@wip
Feature: Grok Messaging
  Grok uses the OpenAI-compatible chat completions API.

  Background:
    Given an in-memory Isaac state directory "target/test-state"
    And the following models exist:
      | alias | model | provider    | contextWindow |
      | count | count | grover:grok | 131072        |
    And the following crew exist:
      | name  | soul               | model |
      | count | Counts everything. | count |

  Scenario: Provider-returned model version is stored in transcript
    Given the following sessions exist:
      | name     | crew  |
      | counting | count |
    And session "counting" has transcript:
      | type    | message.role | message.content           |
      | message | user         | How many bats do you see? |
    And the following model responses are queued:
      | model          | type | content                 |
      | count-20250710 | text | One! One bat! Ah ah ah! |
    When the user sends "How many bats do you see?" on session "counting"
    Then session "counting" has transcript matching:
      | type    | message.role | message.model  | message.provider |
      | message | assistant    | count-20250710 | grover:grok      |

  Scenario: Configured model is stored when provider returns no model
    Given the following sessions exist:
      | name     | crew  |
      | counting | count |
    And session "counting" has transcript:
      | type    | message.role | message.content  |
      | message | user         | How many clouds? |
    And the following model responses are queued:
      | model | type | content                    |
      |       | text | Two! Two clouds! Ah ah ah! |
    When the user sends "How many clouds?" on session "counting"
    Then session "counting" has transcript matching:
      | type    | message.role | message.model |
      | message | assistant    | count         |
