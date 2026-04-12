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
