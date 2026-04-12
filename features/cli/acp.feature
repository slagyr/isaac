Feature: ACP command
  `isaac acp` starts Isaac as an ACP agent over stdio. It reads
  JSON-RPC messages from stdin, writes responses to stdout, and
  loops until stdin closes. Method-level behavior is covered by
  features/acp/*.feature via direct handler dispatch; this feature
  only verifies the CLI loop plumbs stdin and stdout correctly.

  Background:
    Given an empty Isaac state directory "target/test-state"
    And the following models exist:
      | alias  | model | provider | contextWindow |
      | grover | echo  | grover   | 32768         |
    And the following agents exist:
      | name | soul           | model  |
      | main | You are Isaac. | grover |

  Scenario: acp command is registered and has help
    When isaac is run with "help acp"
    Then the output contains "Usage: isaac acp"
    And the exit code is 0

  Scenario: acp command reads a request from stdin and writes a response to stdout
    Given stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1,"clientInfo":{"name":"test","version":"0.1"}}}
      """
    When isaac is run with "acp"
    Then the output contains "protocolVersion"
    And the output contains "agentInfo"
    And the exit code is 0

  Scenario: acp command loops over multiple stdin requests
    Given stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      {"jsonrpc":"2.0","id":2,"method":"session/new","params":{"cwd":"/tmp/test"}}
      """
    When isaac is run with "acp"
    Then the output contains "protocolVersion"
    And the output contains "sessionId"
    And the exit code is 0

  Scenario: acp command exits cleanly on stdin EOF
    Given stdin is empty
    When isaac is run with "acp"
    Then the exit code is 0

  Scenario: acp command prints a ready signal to stderr on startup
    Given stdin is empty
    When isaac is run with "acp"
    Then the stderr contains "isaac acp ready"
    And the exit code is 0

  Scenario: --verbose enables debug logging to stderr
    Given stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      """
    When isaac is run with "acp --verbose"
    Then the stderr contains "initialize"
    And the exit code is 0

  Scenario: --session attaches the acp command to an existing session
    Given agent "main" has sessions:
      | key                         |
      | agent:main:acp:direct:user1 |
    And session "agent:main:acp:direct:user1" has transcript:
      | type    | message.role | message.content |
      | message | user         | earlier         |
      | message | assistant    | earlier reply   |
    And stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      {"jsonrpc":"2.0","id":2,"method":"session/new","params":{}}
      """
    When isaac is run with "acp --session agent:main:acp:direct:user1"
    Then the output contains "\"sessionId\":\"agent:main:acp:direct:user1\""
    And the exit code is 0

  Scenario: --session fails if the session does not exist
    When isaac is run with "acp --session agent:main:acp:direct:nonexistent"
    Then the stderr contains "session not found"
    And the stderr contains "agent:main:acp:direct:nonexistent"
    And the exit code is 1

  Scenario: acp resolves main agent from config defaults when no agent list is configured
    Given isaac home "target/test-home" contains config:
      """
      {"agents": {"defaults": {"model": "grover/echo"}},
       "models": {"providers": [{"name": "grover", "baseUrl": "http://fake"}]}}
      """
    And agent "main" has sessions:
      | key                        |
      | agent:main:acp:direct:test |
    And the following model responses are queued:
      | type | content | model |
      | text | Hello   | echo  |
    And stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"agent:main:acp:direct:test","prompt":[{"type":"text","text":"hi"}]}}
      """
    When isaac is run with "acp --session agent:main:acp:direct:test"
    Then the output contains "\"stopReason\":\"end_turn\""
    And the exit code is 0

  Scenario: acp falls back to hardcoded defaults when no isaac.json exists
    Given isaac home "target/test-home" has no config file
    And stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      """
    When isaac is run with "acp"
    Then the output contains "\"protocolVersion\":"
    And the exit code is 0

  Scenario: acp returns an error when agent resolution yields no model
    Given isaac home "target/test-home" contains config:
      """
      {"agents": {"defaults": {}}}
      """
    And agent "main" has sessions:
      | key                        |
      | agent:main:acp:direct:test |
    And stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"agent:main:acp:direct:test","prompt":[{"type":"text","text":"hi"}]}}
      """
    When isaac is run with "acp --session agent:main:acp:direct:test"
    Then the stderr contains "no model configured for agent"
    And the exit code is 0

  @wip
  Scenario: acp uses workspace SOUL.md when no soul in agent config
    Given isaac home "target/test-home" contains config:
      """
      {"agents": {"defaults": {"model": "grover/echo"}},
       "models": {"providers": [{"name": "grover", "baseUrl": "http://fake"}]}}
      """
    And workspace "main" in "target/test-home" has SOUL.md:
      """
      You are Dr. Prattlesworth, a Victorian recluse.
      """
    And agent "main" has sessions:
      | key                        |
      | agent:main:acp:direct:test |
    And the following model responses are queued:
      | type | content | model |
      | text | Hello   | echo  |
    And stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"agent:main:acp:direct:test","prompt":[{"type":"text","text":"hi"}]}}
      """
    When isaac is run with "acp --session agent:main:acp:direct:test"
    Then the output contains "\"stopReason\":\"end_turn\""
    And the exit code is 0

  @wip
  Scenario: acp falls back to default soul when no SOUL.md exists
    Given isaac home "target/test-home" contains config:
      """
      {"agents": {"defaults": {"model": "grover/echo"}},
       "models": {"providers": [{"name": "grover", "baseUrl": "http://fake"}]}}
      """
    And agent "main" has sessions:
      | key                        |
      | agent:main:acp:direct:test |
    And the following model responses are queued:
      | type | content | model |
      | text | Hello   | echo  |
    And stdin is:
      """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
      {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"agent:main:acp:direct:test","prompt":[{"type":"text","text":"hi"}]}}
      """
    When isaac is run with "acp --session agent:main:acp:direct:test"
    Then the output contains "\"stopReason\":\"end_turn\""
    And the exit code is 0
