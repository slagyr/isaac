Feature: ACP Streaming Updates
  As the LLM generates chunks, the agent emits one session/update
  notification per chunk so front-ends can render text incrementally.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following agents exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |
    And agent "main" has sessions:
      | key                         |
      | agent:main:acp:direct:user1 |
    And the ACP client has initialized

  @wip
  Scenario: Provider text chunks are forwarded as session/update notifications
    Given the following model responses are queued:
      | type | content                           | model |
      | text | ["Once " "upon " "a " "time..."] | echo  |
    When the ACP client sends request 20:
      | key                   | value                       |
      | method                | session/prompt              |
      | params.sessionId      | agent:main:acp:direct:user1 |
      | params.prompt[0].type | text                        |
      | params.prompt[0].text | Tell me a story             |
    Then the ACP agent sends notifications:
      | method         | params.update.sessionUpdate | params.update.text |
      | session/update | agent_message_chunk         | Once               |
      | session/update | agent_message_chunk         | upon               |
      | session/update | agent_message_chunk         | a                  |
      | session/update | agent_message_chunk         | time...            |
    And session "agent:main:acp:direct:user1" has transcript matching:
      | type    | message.role | message.content     |
      | message | assistant    | Once upon a time... |
