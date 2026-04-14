Feature: ACP Streaming Updates
  As the LLM generates chunks, the agent emits one session/update
  notification per chunk so front-ends can render text incrementally.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following crew exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |
    And the following sessions exist:
      | name        |
      | stream-test |
    And the ACP client has initialized

  Scenario: Provider text chunks are forwarded as session/update notifications
    Given the following model responses are queued:
      | type | content                           | model |
      | text | ["Once " "upon " "a " "time..."] | echo  |
    When the ACP client sends request 20:
      | key                   | value          |
      | method                | session/prompt |
      | params.sessionId      | stream-test    |
      | params.prompt[0].type | text           |
      | params.prompt[0].text | Tell me a story |
    Then the ACP agent sends notifications:
      | method         | params.update.sessionUpdate | params.update.content.type | params.update.content.text |
      | session/update | agent_message_chunk         | text                       | Once                       |
      | session/update | agent_message_chunk         | text                       | upon                       |
      | session/update | agent_message_chunk         | text                       | a                          |
      | session/update | agent_message_chunk         | text                       | time...                    |
    And session "stream-test" has transcript matching:
      | type    | message.role | message.content     |
      | message | assistant    | Once upon a time... |
