Feature: Session Keys
  Session keys encode agent, channel, chat type, and conversation
  identity following OpenClaw's convention. The key reflects the
  session's origin; routing metadata in the session entry tracks
  the current channel.

  Background:
    Given an empty Isaac state directory "target/test-state"

  # --- Key Construction ---

  Scenario: CLI direct session key
    When the following sessions are created:
      | agent | channel | chatType | conversation |
      | main  | cli     | direct   | micah        |
    Then the session listing has entries matching:
      | key                         |
      | agent:main:cli:direct:micah |

  Scenario: Telegram group session key
    When the following sessions are created:
      | agent | channel  | chatType | conversation |
      | main  | telegram | group    | 12345        |
    Then the session listing has entries matching:
      | key                             |
      | agent:main:telegram:group:12345 |

  Scenario: Thread session key
    Given the following sessions exist:
      | key                            |
      | agent:main:slack:group:general |
    When the following thread sessions are created:
      | parentKey                      | thread |
      | agent:main:slack:group:general | ts-001 |
    Then the session listing has entries matching:
      | key                                          |
      | agent:main:slack:group:general:thread:ts-001 |

  Scenario: Named agent session key
    When the following sessions are created:
      | agent      | channel | chatType | conversation |
      | researcher | cli     | direct   | micah        |
    Then the session listing has entries matching:
      | key                               |
      | agent:researcher:cli:direct:micah |

  # --- Key Parsing ---

  Scenario: Parse a session key
    When the key "agent:main:telegram:group:12345" is parsed
    Then the parsed key matches:
      | key          | value    |
      | agent        | main     |
      | channel      | telegram |
      | chatType     | group    |
      | conversation | 12345    |

  # --- Routing ---

  Scenario: Session tracks last delivery route
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:micah |
    When the following messages are appended:
      | role | content | channel | to    |
      | user | Hello   | cli     | micah |
    Then the session listing has entries matching:
      | key                         | lastChannel | lastTo |
      | agent:main:cli:direct:micah | cli         | micah  |

  Scenario: Delivery route updates when channel changes
    Given the following sessions exist:
      | key                         |
      | agent:main:cli:direct:micah |
    And the following messages are appended:
      | role | content | channel | to    |
      | user | Hello   | cli     | micah |
    When the following messages are appended:
      | role | content     | channel  | to    |
      | user | Hello again | telegram | micah |
    Then the session listing has entries matching:
      | key                         | lastChannel | lastTo |
      | agent:main:cli:direct:micah | telegram    | micah  |
