Feature: Per-crew filesystem boundaries
  Each crew member can only access their quarters and explicitly
  whitelisted directories. File operations outside these boundaries
  are rejected.

  Background:
    Given an in-memory Isaac state directory "isaac-state"
    And config file "isaac.edn" containing:
      """
      {:defaults  {:crew :main :model :echo}
       :providers {:grover {:baseUrl "http://test" :api "grover"}}
       :models    {:echo {:model "echo" :provider :grover :contextWindow 32768}}}
      """

  Scenario: crew can read files in their quarters
    Given config file "crew/main.edn" containing:
      """
      {:tools {:allow [:read]}}
      """
    And crew "main" has file "notes.txt" with "hello"
    And the following sessions exist:
      | name       |
      | fence-test |
    And the following model responses are queued:
      | type      | tool | arguments                                       |
      | tool_call | read | {"filePath": "/isaac-state/crew/main/notes.txt"} |
      | text      |      | Got it                                          |
    When the user sends "read notes" on session "fence-test"
    Then session "fence-test" has transcript matching:
      | type    | message.role | message.isError |
      | message | toolResult   |                 |

  Scenario: crew can read files in whitelisted directories
    Given config file "crew/main.edn" containing:
      """
      {:tools {:allow       [:read]
               :directories ["/tmp/isaac-playground"]}}
      """
    And file "/tmp/isaac-playground/data.txt" contains "hello"
    And the following sessions exist:
      | name       |
      | fence-test |
    And the following model responses are queued:
      | type      | tool | arguments                                     |
      | tool_call | read | {"filePath": "/tmp/isaac-playground/data.txt"} |
      | text      |      | Got it                                        |
    When the user sends "read data" on session "fence-test"
    Then session "fence-test" has transcript matching:
      | type    | message.role | message.isError |
      | message | toolResult   |                 |

  Scenario: crew cannot read files outside their boundaries
    Given config file "crew/main.edn" containing:
      """
      {:tools {:allow [:read]}}
      """
    And the following sessions exist:
      | name       |
      | fence-test |
    And the following model responses are queued:
      | type      | tool | arguments                   |
      | tool_call | read | {"filePath": "/etc/passwd"} |
      | text      |      | Sorry                       |
    When the user sends "read passwords" on session "fence-test"
    Then session "fence-test" has transcript matching:
      | type    | message.role | message.isError | message.content                  |
      | message | toolResult   | true            | path outside allowed directories |

  Scenario: crew cannot write files outside their boundaries
    Given config file "crew/main.edn" containing:
      """
      {:tools {:allow [:write]}}
      """
    And the following sessions exist:
      | name       |
      | fence-test |
    And the following model responses are queued:
      | type      | tool  | arguments                                          |
      | tool_call | write | {"filePath": "/tmp/evil.txt", "content": "gotcha"} |
      | text      |       | Sorry                                              |
    When the user sends "write evil" on session "fence-test"
    Then session "fence-test" has transcript matching:
      | type    | message.role | message.isError | message.content                  |
      | message | toolResult   | true            | path outside allowed directories |

  Scenario: crew can access session cwd when it opts in via :cwd
    Given config file "crew/main.edn" containing:
      """
      {:tools {:allow       [:read]
               :directories [:cwd]}}
      """
    And file "/work/project/hello.txt" contains "hi there"
    And the following sessions exist:
      | name       | cwd           |
      | fence-test | /work/project |
    And the following model responses are queued:
      | type      | tool | arguments                               |
      | tool_call | read | {"filePath": "/work/project/hello.txt"} |
      | text      |      | Got it                                  |
    When the user sends "read hello" on session "fence-test"
    Then session "fence-test" has transcript matching:
      | type    | message.role | message.isError |
      | message | toolResult   |                 |

  Scenario: crew without :cwd opt-in cannot access session cwd
    Given config file "crew/main.edn" containing:
      """
      {:tools {:allow [:read]}}
      """
    And file "/work/project/hello.txt" contains "hi there"
    And the following sessions exist:
      | name       | cwd           |
      | fence-test | /work/project |
    And the following model responses are queued:
      | type      | tool | arguments                               |
      | tool_call | read | {"filePath": "/work/project/hello.txt"} |
      | text      |      | Sorry                                   |
    When the user sends "read hello" on session "fence-test"
    Then session "fence-test" has transcript matching:
      | type    | message.role | message.isError | message.content                  |
      | message | toolResult   | true            | path outside allowed directories |

  Scenario: path traversal that escapes boundaries is rejected
    Given config file "crew/main.edn" containing:
      """
      {:tools {:allow [:read]}}
      """
    And the following sessions exist:
      | name       |
      | fence-test |
    And the following model responses are queued:
      | type      | tool | arguments                                                |
      | tool_call | read | {"filePath": "/isaac-state/crew/main/../../etc/passwd"} |
      | text      |      | Sorry                                                   |
    When the user sends "sneaky read" on session "fence-test"
    Then session "fence-test" has transcript matching:
      | type    | message.role | message.isError | message.content                  |
      | message | toolResult   | true            | path outside allowed directories |

  Scenario: crew cannot read its own config file
    Given config file "crew/main.edn" containing:
      """
      {:tools {:allow [:read]}}
      """
    And the following sessions exist:
      | name       |
      | fence-test |
    And the following model responses are queued:
      | type      | tool | arguments                                          |
      | tool_call | read | {"filePath": "/isaac-state/config/crew/main.edn"} |
      | text      |      | Sorry                                             |
    When the user sends "read my config" on session "fence-test"
    Then session "fence-test" has transcript matching:
      | type    | message.role | message.isError | message.content                  |
      | message | toolResult   | true            | path outside allowed directories |
